# WildFly Core - GraalVM Modifications

## Summary

The WildFly Core modifications enable:

1. **Two-Phase Execution**:
   - `preMain()` starts server in suspended mode at build time
   - `main()` resumes from suspended state at runtime

2. **Service Passivation/Activation**:
   - Services passivated before native image compilation
   - Services activated at runtime to resume operations

3. **Network Interface Handling**:
   - Interface criteria passivate to clear InetAddress references
   - Socket bindings recreated at runtime

4. **I/O Worker Management**:
   - XNIO workers support passivation/activation
   - Worker threads recreated at runtime

5. **Remoting Support**:
   - Remoting endpoints support lifecycle
   - Channel listeners handle passivation

6. **Request Controller**:
   - Timers cleared from heap at build time

7. **Management Interface**:
   - Undertow HTTP listeners delayed until after activation
   - Management workers support lifecycle

8. **Module Dependencies**:
   - `wildfly-graal-runtime` module added to core modules
   - Provides `WildFlyGraalSetup` utility for build/runtime detection

## Overview

**Tag**: `2026_july_end_phase2`  
**Total Changes**: 52 files modified, 662 lines added, 146 lines deleted

## Module: server/

### server/src/main/java/org/jboss/as/server/Main.java

Added `preMain()` method for build-time initialization:

```java
private static BootstrapImpl impl;

public static void preMain(String[] args) throws Exception {
    System.out.println("Static Initialization of the server");
    List<String> list = new ArrayList<>(Arrays.asList(args));
    list.add("--start-mode=suspend");
    
    impl = (BootstrapImpl)doMain(list.toArray(String[]::new));
    int timeout = Integer.getInteger("org.wildfly.graal.build.time.timeout", 10000);
    System.out.println("We are done, waiting " + timeout + "ms for the server to stabilize.");
    Thread.sleep(timeout);
    System.out.println("We are done starting the server, passivate services");
    impl.passivateServices();
}
```

Modified `main()` for runtime execution:

```java
public static void main(String[] args) throws Exception {
    long startTime = System.currentTimeMillis();
    if (impl == null) {
        impl = (BootstrapImpl) doMain(args);
    } else {
        org.jboss.modules.ref.References.startReaperThread();
        impl.finishBoot(startTime);
    }
}
```

Disabled stdio setup at build time:

```java
if (java.util.logging.LogManager.getLogManager().getClass().getName().equals("org.jboss.logmanager.LogManager") &&
        !WildFlyGraalSetup.isBuildTime()) {
    // Install JBoss Stdio
}
```

### server/src/main/java/org/jboss/as/server/BootstrapImpl.java

Added passivation and finishBoot methods:

```java
void passivateServices() {
    container.passivateServices();
}

public void finishBoot(long startTime) throws ConfigurationPersistenceException {
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    applicationServerService.finishBoot(startTime);
}
```

Stored `ApplicationServerService` reference:

```java
private ApplicationServerService applicationServerService;
```

Disabled MBean registration and FD checks at build time, prevented shutdown at build time.

### server/src/main/java/org/jboss/as/server/ApplicationServerService.java

Stored `ServerService` reference for activation:

```java
private ServerService serverService;

// In start():
serverService = ServerService.addService(serviceTarget, configuration, processState, ...);
```

Added `finishBoot()` method to complete server startup at runtime:

```java
public void finishBoot(long startTime) throws ConfigurationPersistenceException {
    ServerLogger.AS_ROOT_LOGGER.serverStarting(prettyVersion, banner);
    // Activate Undertow listeners last (due to SSLContext issues)
    futureContainer.get().activateServices("org.wildfly.undertow.listener.");
    serverService.finishBoot(false);
    ServerLogger.AS_ROOT_LOGGER.startedClean("Server started in " + (System.currentTimeMillis() - startTime) + "ms");
}
```

### server/src/main/java/org/jboss/as/server/ServerService.java

Wrapped `ExecutorService` to support passivation/activation:

```java
private class ExecutorServiceDelegate implements ExecutorService {
    private ExecutorService delegate;
    
    private void init() {
        // Create actual executor (EnhancedQueueExecutor or ThreadPoolExecutor)
        delegate = new EnhancedQueueExecutor.Builder()
            .setCorePoolSize(getCorePoolSize(forDomain))
            .setMaximumPoolSize(getMaxPoolSize())
            .setKeepAliveTime(20L, TimeUnit.SECONDS)
            .setThreadFactory(threadFactory)
            .build();
    }
    
    // All ExecutorService methods delegate to 'delegate'
}
```

Changed `ServerExecutorService`:

```java
private ExecutorServiceDelegate executorService;

@Override
public void start(StartContext context) throws StartException {
    executorService = new ExecutorServiceDelegate();
    executorService.init();  // Create executor at build time
}

@Override
public void passivate() {
    executorService.shutdownNow();  // Shut down threads before compilation
}

@Override
public void activate() throws StartException {
    executorService.init();  // Recreate executor at runtime
    // Submit NOP task to ensure at least one non-daemon thread runs
    executorService.submit(() -> {});
}
```

Disabled annotation/reflection index cleanup at build time:

```java
if (!WildFlyGraalSetup.isBuildTime()) {
    DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.CLEANUP, 
        Phase.CLEANUP_REFLECTION_INDEX, new CleanupReflectionIndexProcessor());
    DeployerChainAddHandler.addDeploymentProcessor(SERVER_NAME, Phase.CLEANUP, 
        Phase.CLEANUP_ANNOTATION_INDEX, new CleanupAnnotationIndexProcessor());
} else {
    ServerLogger.ROOT_LOGGER.warn("At build time do not install cleaners to keep Reflection index");
}
```

The reflection/annotation indexes must be preserved in the heap for runtime use.

### server/src/main/java/org/jboss/as/server/ServerPathManagerService.java

Added passivation/activation lifecycle methods (empty implementations - no state to manage).

### server/src/main/java/org/jboss/as/server/deployment/

#### DeploymentMountProvider.java

Added passivation/activation for VFS mounts:

```java
private JBossThreadFactory threadFactory;  // Store for runtime recreation

@Override
public void start(StartContext context) throws StartException {
    threadFactory = new JBossThreadFactory(ThreadGroupHolder.THREAD_GROUP, true, null, "%G - %t", null, null);
    scheduledExecutorService = new ScheduledThreadPoolExecutor(2, threadFactory);
    tempFileProvider = TempFileProvider.create("deployment", scheduledExecutorService, true);
}

@Override
public void stop(final StopContext context) {
    if (WildFlyGraalSetup.isRuntime()) {
        ServerLogger.ROOT_LOGGER.info("VFS Mounted files are not stopped");
        return;  // Prevent unmounting at runtime
    }
    // ... normal cleanup
}

@Override
public void passivate() {
    scheduledExecutorService.shutdownNow();
    scheduledExecutorService = null;
    JavaZipFileSystemGraal.passivateFiles(tempFileProvider);  // Null out zip file delegates
}

@Override
public void activate() throws StartException {
    JavaZipFileSystemGraal.activateFiles(tempFileProvider);  // Recreate zip file delegates
}
```

VFS mount points remain in the heap; only the underlying file system delegates are passivated/reactivated.

#### ContentCleanerService.java

Deployment cleanup with passivation (8 lines added):

```java
@Override
public void passivate() {
}
```

### server/src/main/java/org/jboss/as/server/deployment/module/

#### ModuleSpecProcessor.java

Integrated with JBoss Modules ClassCache for deployment module specs (passes cache instance to module loader).

### server/src/main/java/org/jboss/as/server/moduleservice/

#### ServiceModuleLoader.java

Attached ClassCache instances to JBoss Modules for deployment classloading.

#### ModuleLoadService.java

Module loading with passivation (2 lines added):

```java
@Override
public void passivate() {
}
```

### server/src/main/java/org/jboss/as/server/services/net/

#### NetworkInterfaceService.java

Added passivation/activation for network interface bindings:

```java
private OverallInterfaceCriteria criteria;
private final Set<InterfaceCriteria> parsedCriteria;  // Store parsed criteria for recreation

@Override
public void passivate() {
    this.criteria = null;
    this.interfaceBinding = null;
    for (InterfaceCriteria c : parsedCriteria) {
        c.passivate();  // Clear InetAddress references
    }
}

@Override
public void activate() throws StartException {
    if (this.criteria != null) {
        return;
    }
    for (InterfaceCriteria c : parsedCriteria) {
        c.activate();
    }
    this.criteria = new OverallInterfaceCriteria(name, parsedCriteria);
    start(null);  // Re-resolve network interface
}

@Override
public NetworkInterfaceBinding getValue() {
    NetworkInterfaceBinding binding = this.interfaceBinding;
    if (binding == null && WildFlyGraalSetup.isRuntime()) {
        activate();  // Lazy activation on first access
        binding = this.interfaceBinding;
    }
    return binding;
}
```

Network interfaces are nulled at passivation and re-resolved at activation to avoid InetAddress references in the heap.

#### SocketBindingService.java

Added passivation/activation lifecycle methods for socket bindings.

#### BindingAddHandler.java

Updated to support passivation/activation of socket binding handlers.

### server/src/main/java/org/jboss/as/server/mgmt/

#### UndertowHttpManagementService.java

HTTP management service with delayed listener activation (52 lines modified).

Commit message notes: "Delay init of undertow listeners at the end, should be revisited, due to SSLContext"

#### ManagementWorkerService.java

Added passivation/activation for management XNIO worker (uses XnioWorkerSupplier pattern).

## Module: controller/

### controller/src/main/java/org/jboss/as/controller/interfaces/

#### InterfaceCriteria.java

Added passivation interface method:

```java
/**
 * Passivate the criteria, called in a Graal VM context.
 */
default void passivate() {
}
```

#### InetAddressMatchInterfaceCriteria.java

Implements passivation to clear InetAddress references (5 lines modified):

```java
@Override
public void passivate() {
    // Clear InetAddress references
}
```

#### LoopbackAddressInterfaceCriteria.java

Added passivate implementation (5 lines added).

### controller/src/main/java/org/jboss/as/controller/

#### ModelControllerImpl.java

Deferred `Random` instance creation to avoid static initialization at build time:

```java
private Random random;  // Changed from 'final Random random = new Random()'

// In constructor:
if (!WildFlyGraalSetup.isBuildTime()) {
    random = new Random();
}

private Random getRandom() {
    Random ret = random;
    if (ret == null) {
        ret = new Random();  // Create on-demand at runtime
    }
    return ret;
}

// Usage:
final Integer operationID = getRandom().nextInt();
```

Random instance is created on-demand at runtime instead of statically at build time.

### controller/src/main/java/org/jboss/as/controller/security/

#### CredentialReference.java

Added passivation/activation support for credential references.

## Module: elytron/

### elytron/src/main/java/org/wildfly/extension/elytron/

#### TrivialService.java

Added passivation/activation lifecycle:

```java
@Override
public void passivate() {
    value = null;  // Clear the value
}

@Override
public void activate() throws StartException {
    value = valueSupplier.get();  // Recreate value from supplier
    if (valueConsumer != null) {
        valueConsumer.accept(value);  // Notify consumer
    }
}
```

This fix ensures that consumers are notified when services are activated at runtime.

#### ProviderRegistrationService.java

Added `activate()` method (9 lines added):

```java
@Override
public void activate() throws StartException {
    start(null);
}
```

#### PermissionMapperDefinitions.java

Added passivation/activation support for permission mappers (this change is meaningless, done pre-JDK25 when permissions were still relevant).

## Module: io/

### io/spi/src/main/java/org/wildfly/io/

#### IOServiceDescriptor.java

Updated service descriptor to return `XnioWorkerSupplier` instead of `XnioWorker` directly.

#### XnioWorkerSupplier.java

**New file** (33 lines added): Wrapper for XnioWorker that supports passivation/activation lifecycle.

```java
public class XnioWorkerSupplier {
    XnioWorker worker;
    private final XnioWorker.Builder builder;

    public XnioWorkerSupplier(XnioWorker.Builder builder) {
        this.builder = builder;
    }

    public void init() {
        worker = builder.build();
    }
    
    public void cleanup() {
        worker = null;
    }

    public XnioWorker get() {
        return worker;
    }
}
```

**Why this is needed**: 

XNIO workers manage I/O thread pools. In GraalVM native images, threads cannot be frozen in the heap. The problem:

1. **At build time**: If we create the `XnioWorker` directly, its thread pool would be active
2. **During compilation**: GraalVM cannot serialize active threads into the native image
3. **Solution**: Store only the `XnioWorker.Builder` in the heap

The `XnioWorkerSupplier`:
- **Build time**: Stores the builder configuration, creates worker via `init()`, then calls `cleanup()` to null it out before compilation
- **Runtime**: Calls `init()` again to recreate the worker with the same builder configuration
- Acts as an indirection layer that allows the worker to be recreated at runtime from a stored builder

This pattern is used throughout for any service that manages threads (workers, executors, thread pools).

### io/subsystem/src/main/java/org/wildfly/extension/io/

#### WorkerService.java

Worker service with passivation/activation (33 lines modified).

Changed to use `XnioWorkerSupplier` instead of `XnioWorker` directly:

```java
public final class WorkerService implements Service<XnioWorkerSupplier> {
    private final XnioWorker.Builder builder;
    private XnioWorkerSupplier workerSupplier;

    @Override
    public void start(final StartContext startContext) {
        builder.setTerminationTask(this::stopDone);
        workerSupplier = new XnioWorkerSupplier(builder);
        workerSupplier.init();  // Create worker at build time
        workerConsumer.accept(workerSupplier);
    }

    @Override
    public void passivate() {
        workerSupplier.cleanup();  // Null out worker before compilation
    }

    @Override
    public void activate() throws StartException {
        workerSupplier.init();  // Recreate worker at runtime
    }

    @Override
    public XnioWorkerSupplier getValue() {
        return workerSupplier;
    }
}
```

The service provides the `XnioWorkerSupplier` to consumers instead of the `XnioWorker` directly. Consumers call `workerSupplier.get()` to access the actual worker.

#### WorkerAdd.java

Updated worker add handler to support XnioWorkerSupplier lifecycle.

#### WorkerResourceDefinition.java

Updated resource definition for XnioWorkerSupplier-based workers.

#### IOServices.java

Updated service capability to provide XnioWorkerSupplier.

#### IOSubsystemResourceDefinitionRegistrar.java

Updated subsystem registrar for I/O worker changes.

## Module: remoting/

### remoting/subsystem/src/main/java/org/jboss/as/remoting/

#### EndpointService.java

Added passivation/activation for JBoss Remoting endpoints.

#### RemotingHttpUpgradeService.java

Added passivation/activation for HTTP upgrade service.

#### AbstractChannelOpenListenerService.java

Added passivation/activation for channel listener services.

#### RemotingSubsystemAdd.java

Updated subsystem add handler for passivation/activation support.

## Module: request-controller/

### request-controller/src/main/java/org/wildfly/extension/requestcontroller/

#### RequestController.java

Request controller with passivation (9 lines added):

```java
@Override
public void passivate() {
    // No Timer in the heap
}
```

Commit `2b14e31552` notes: "Request controller, no Timer in the heap"

## Module: core-feature-pack/

### core-feature-pack/common/src/main/resources/modules/system/layers/base/

Added `wildfly-graal-runtime` dependency to module.xml files:

Each module.xml adds:
```xml
<module name="org.wildfly.graal.runtime" export="true"/>
```

This makes `WildFlyGraalSetup` utility class available to:
- `org/jboss/as/controller/main/module.xml` - for controller components
- `org/jboss/msc/main/module.xml` - for service container
- `org/jboss/remoting/main/module.xml` - for remoting services
- `org/jboss/vfs/main/module.xml` - for VFS operations
- `org/jboss/xnio/main/module.xml` - for XNIO workers
- `org/wildfly/security/elytron-base/main/module.xml` - for security providers
- `io/undertow/core/main/module.xml` - for HTTP listeners

Created new module:

#### org/wildfly/graal/runtime/main/module.xml

New module descriptor for wildfly-graal-runtime:

```xml
<module xmlns="urn:jboss:module:1.9" name="org.wildfly.graal.runtime">
    <resources>
        <artifact name="${org.wildfly.graal:wildfly-graal-runtime}"/>
    </resources>
    <dependencies>
        <module name="org.jboss.modules"/>
    </dependencies>
</module>
```

Provides `WildFlyGraalSetup` utility class with methods:
- `isBuildTime()` / `isRuntime()` - detect build vs runtime phase
- `addClassToCache()` - register class in JBoss Modules cache
- `getClassFromCache()` - retrieve cached class
- `getConstructorFromCache()` - retrieve cached constructor

#### jakarta/json/api/main/module.xml

Added `org.wildfly.graal.runtime` dependency.

## Dependencies (pom.xml files)

Added `wildfly-graal-runtime` dependency to:
- `elytron/pom.xml`
- `io/spi/pom.xml`
- `io/subsystem/pom.xml`
- `server/pom.xml`
- `core-feature-pack/common/pom.xml`
- Root `pom.xml`
