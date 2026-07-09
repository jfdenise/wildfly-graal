# WildFly - GraalVM Modifications

## Summary

The WildFly modifications enable the full application server to work in a GraalVM native image context. Changes span three main modules:

**EE Module:**
- Permission handling deferred to runtime
- Class loading changes to avoid GraalVM-forbidden `Class.forName()`

**Undertow Module:**
- ByteBuffer pool with passivate/activate lifecycle
- Console redirect deferred to runtime
- HTTP/HTTPS listeners with deferred startup
- XnioWorker supplier-based lazy initialization
- WebSockets with supplier-based worker initialization
- JSP initialization made optional

**Weld Module:**
- CDI proxy pre-generation at build time
- Bean validation metadata caching (experimental)
- Reflection using cached constructors
- Executor services with passivate/activate lifecycle

**Module Dependencies:**
- Added `org.wildfly.graal.runtime` to 8 module.xml files
- Exported services for JSON-B provider discovery

## Overview

**Total Changes**: 3 commits, 29 files modified, 352 lines added, 45 lines removed

**Commits:**
1. `150dc515fc` - "WildFly on Graal VM" (main changes)
2. `c4b2276ff7` - "Avoid NPE in weld subsystem" (bug fix)
3. `c9175d8b16` - "Remove Exception thrown if JspRuntimeContext class not found" (cleanup)

## Module: ee-feature-pack/ (Module Dependency Configuration)

### ee-feature-pack/galleon-shared/src/main/resources/modules/system/layers/base/io/undertow/servlet/main/module.xml

Added GraalVM runtime dependency:

```xml
<module name="org.wildfly.graal.runtime"/>
```

### ee-feature-pack/galleon-shared/src/main/resources/modules/system/layers/base/io/undertow/websocket/main/module.xml

Added GraalVM runtime dependency:

```xml
<module name="org.wildfly.graal.runtime"/>
```

### ee-feature-pack/galleon-shared/src/main/resources/modules/system/layers/base/jakarta/json/bind/api/main/module.xml

Exported Yasson services for ServiceLoader discovery:

```xml
<!-- Services must be exported for the Service Loader to find the default provider, required by Graal mode -->
<module name="org.eclipse.yasson" export="true" services="export"/>
```

**Why this is needed**:

GraalVM's ServiceLoader substitution requires service implementations to be visible and exported at build time.

### ee-feature-pack/galleon-shared/src/main/resources/modules/system/layers/base/org/jboss/as/weld/beanvalidation/main/module.xml

Added GraalVM runtime dependency:

```xml
<module name="org.wildfly.graal.runtime"/>
```

### ee-feature-pack/galleon-shared/src/main/resources/modules/system/layers/base/org/jboss/resteasy/*.xml

Added GraalVM runtime dependency to:
- `resteasy-cdi/main/module.xml`
- `resteasy-core/main/module.xml`
- `resteasy-jsapi/main/module.xml`
- `resteasy-json-binding-provider/main/module.xml`

```xml
<module name="org.wildfly.graal.runtime"/>
```

## Module: ee/ (Enterprise Edition Core)

### ee/src/main/java/org/jboss/as/ee/component/deployers/EEDefaultPermissionsProcessor.java

Deferred permission setup to runtime:

```java
// Permissions are becoming irrelevant, we can ignore them
if (!WildFlyGraalSetup.isBuildTime()) {
    //make sure they can read the contents of the deployment
    ResourceRoot root = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
    try {
        File file = root.getRoot().getPhysicalFile();
        if (file != null && file.isDirectory()) {
            FilePermission permission = new FilePermission(file.getAbsolutePath() + File.separatorChar + "-", "read");
            permissions.add(new ImmediatePermissionFactory(permission));
        }
    } catch (IOException ex) {
        throw new DeploymentUnitProcessingException(ex);
    }
}
```

**Why this is needed**:

Permissions are becoming deprecated/irrelevant in modern Java. File operations at build time can fail or create frozen paths in the native image.

### ee/src/main/java/org/jboss/as/ee/utils/ClassLoadingUtils.java

Replaced `Class.forName()` with `ClassLoader.loadClass()`:

```java
// Can retrieve the class from the cache.
// Class.forName is detected by Graal VM and forbidden.
return module.getClassLoader().loadClass(className, false);
```

**Why this is needed**:

GraalVM detects and restricts `Class.forName()` usage. Using `ClassLoader.loadClass()` allows retrieval from JBoss Modules cache.

## Module: undertow/ (HTTP Server Subsystem)

### undertow/src/main/java/org/wildfly/extension/undertow/ByteBufferPoolDefinition.java

Implemented delegate pattern with passivate/activate lifecycle:

```java
private class ByteBufferPoolDelegate implements ByteBufferPool {
    ByteBufferPool delegate;
    
    private void init() {
        delegate = new DefaultByteBufferPool(direct, size, maxSize, threadLocalCacheSize, leakDetectionPercent);
    }
    
    private void passivate() {
        delegate = null;
    }
    
    @Override
    public PooledByteBuffer allocate() {
        return delegate.allocate();
    }
    // ... other ByteBufferPool methods
}

@Override
public void start(StartContext startContext) throws StartException {
    pool = new ByteBufferPoolDelegate();
    pool.init();
}

@Override
public void passivate() {
    pool.close();
    pool.passivate();
}

@Override
public void activate() {
    pool.init();
}
```

**Why this is needed**:

`ByteBufferPool` creates native memory allocations that cannot be frozen in the native image heap. The delegate pattern allows cleanup at build time (passivate) and re-creation at runtime (activate).

### undertow/src/main/java/org/wildfly/extension/undertow/ConsoleRedirectService.java

Deferred console redirect to runtime:

```java
private StartContext context;

@Override
public void start(final StartContext startContext) {
    if (WildFlyGraalSetup.isBuildTime()) {
        UndertowLogger.ROOT_LOGGER.info("[WildFly Graal] do not start Console redirect, will be activated at runtime");
        this.context = context;
        return;
    }
    // ... normal startup
}

@Override
public void activate() throws StartException {
    start(context);
}
```

**Why this is needed**:

Console redirect involves network operations that should not execute during native image build.

### undertow/src/main/java/org/wildfly/extension/undertow/HttpsListenerService.java

Changed to use `XnioWorkerSupplier`:

```java
return new UndertowXnioSsl(((XnioWorker)worker.get().get()).getXnio(), combined, sslContext);
```

**Why this is needed**:

`XnioWorker` is wrapped in a supplier to enable deferred initialization.

### undertow/src/main/java/org/wildfly/extension/undertow/ListenerAdd.java

Changed dependency from `XnioWorker` to `XnioWorkerSupplier`:

```java
service.getWorker().set(sb.requiresCapability(REF_IO_WORKER, XnioWorkerSupplier.class, workerName));
```

**Why this is needed**:

Direct `XnioWorker` injection would initialize workers at build time. The supplier pattern defers initialization to runtime.

### undertow/src/main/java/org/wildfly/extension/undertow/ListenerService.java

Implemented passivate/activate lifecycle:

```java
protected final DelegatingSupplier<XnioWorkerSupplier> worker = new DelegatingSupplier<>();

private StartContext context;

@Override
public void activate() throws StartException {
    start(context);
}

@Override
public void start(final StartContext context) throws StartException {
    if (WildFlyGraalSetup.isBuildTime()) {
        UndertowLogger.ROOT_LOGGER.info("[WildFly Graal], undertow not started at build time, will be activated at runtime");
        this.context = context;
        return;
    }
    // ... normal startup
}
```

**Why this is needed**:

HTTP listeners bind to network sockets and start threads, which cannot happen at build time. Services are suspended and activated at runtime.

### undertow/src/main/java/org/wildfly/extension/undertow/ServletContainerAdd.java

Changed to `XnioWorkerSupplier` for WebSockets:

```java
final Supplier<XnioWorkerSupplier> xnioWorker = webSocketInfo != null 
    ? builder.requiresCapability(Capabilities.REF_IO_WORKER, XnioWorkerSupplier.class, webSocketInfo.getWorker()) 
    : null;

@Override
public XnioWorker getWebsocketsWorker() {
    return (xnioWorker != null) ? xnioWorker.get().get() : null;
}

@Override
public XnioWorkerSupplier getWebsocketsWorkerSupplier() {
    return (xnioWorker != null) ? xnioWorker.get() : null;
}
```

**Why this is needed**:

WebSocket workers must be deferred to runtime activation when network operations can begin.

### undertow/src/main/java/org/wildfly/extension/undertow/ServletContainerService.java

Added `getWebsocketsWorkerSupplier()` method:

```java
XnioWorkerSupplier getWebsocketsWorkerSupplier();
```

**Why this is needed**:

Provides access to the worker supplier for deferred initialization.

### undertow/src/main/java/org/wildfly/extension/undertow/UndertowSubsystemAdd.java

Removed exception when JSP runtime not found (commit c9175d8b16):

```java
try {
    Class.forName("org.apache.jasper.compiler.JspRuntimeContext", true, this.getClass().getClassLoader());
} catch (ClassNotFoundException e) {
    UndertowLogger.ROOT_LOGGER.couldNotInitJsp(e);
    // No longer throws OperationFailedException - JSP is optional
}
```

**Why this is needed**:

JSP support is optional. The original code threw an exception that prevented server startup when JSP classes were unavailable.

### undertow/src/main/java/org/wildfly/extension/undertow/deployment/UndertowDeploymentInfoService.java

Changed to supplier-based WebSocket worker:

```java
if(servletContainer.isWebsocketsEnabled() && webSocketDeploymentInfo != null) {
    webSocketDeploymentInfo.setBuffers(servletContainer.getWebsocketsBufferPool());
    Supplier<XnioWorker> supplier = () -> {return servletContainer.getWebsocketsWorkerSupplier().get();};
    webSocketDeploymentInfo.setWorkerSupplier(supplier);
    webSocketDeploymentInfo.setDispatchToWorkerThread(servletContainer.isDispatchWebsocketInvocationToWorker());
    // ...
}
```

**Why this is needed**:

WebSocket deployment info needs lazy worker initialization via supplier pattern.

### undertow/src/main/java/org/wildfly/extension/undertow/deployment/WarStructureDeploymentProcessor.java

Deferred file permission setup:

```java
// Permissions are becoming irrelevant, we can ignore them
if (!WildFlyGraalSetup.isBuildTime()) {
    moduleSpecification.addPermissionFactory(
        new ImmediatePermissionFactory(
            new FilePermission(tempDir.getAbsolutePath() + File.separatorChar + "-", "read,write,delete")
        )
    );
}
```

**Why this is needed**:

File permissions at build time would freeze temp directory paths in the native image.

## Module: weld/ (CDI/Contexts and Dependency Injection Subsystem)

### weld/bean-validation/pom.xml

Added WildFly GraalVM runtime dependency:

```xml
<dependency>
    <groupId>org.wildfly.graal</groupId>
    <artifactId>wildfly-graal-runtime</artifactId>
</dependency>
```

### weld/bean-validation/src/main/java/org/jboss/as/weld/CdiValidatorFactoryService.java

Attempted to force validation metadata creation at build time (commented as experimental):

```java
// That doesn't work, we see CDI proxy being used as key to retrieve Bean metadata
// So forcing those classes is incomplete
// XXX CREMA NOT NEEDED
if (WildFlyGraalSetup.isBuildTime()) {
    Class[] classes = WildFlyGraalSetup.getCDIClasses();
    for (Class clazz : classes) {
        System.out.println("Force Validation metadata creation for " + clazz.getName());
        try {
            validatorFactory.getValidator().getConstraintsForClass(clazz);
        } catch (Exception ex) {
            // OK, attempt to create an instance that can be invalid for transient scopes (e.g.: request).
            System.err.println(ex);
            ex.printStackTrace();
        }
    }
}
```

**Why this is needed** (experimental):

Bean Validation metadata needs to be pre-generated at build time. However, this approach is incomplete because CDI proxies are used as keys, making it difficult to pre-cache all metadata.

### weld/common/src/main/java/org/jboss/as/weld/ServiceNames.java

Added service name for bean registration:

```java
public static final ServiceName WELD_REGISTER_BEANS_SERVICE_NAME = ServiceName.of("WeldRegisterBeansService");
```

### weld/common/src/main/java/org/jboss/as/weld/util/Reflections.java

Use cached constructors at runtime:

```java
public static <T> T newInstance(String className, ClassLoader classLoader) {
    try {
        Class<?> clazz = classLoader.loadClass(className);
        if(WildFlyGraalSetup.isRuntime()) {
            Constructor<T> ctr = WildFlyGraalSetup.getConstructorFromCache(classLoader, clazz);
            return ctr.newInstance();
        } else {
            return (T) clazz.newInstance();
        }
    } catch (IllegalAccessException | ClassNotFoundException | InstantiationException | InvocationTargetException e) {
        throw new RuntimeException(e);
    }
}
```

**Why this is needed**:

GraalVM restricts reflection. Constructors must be pre-cached at build time and retrieved from cache at runtime.

### weld/subsystem/src/main/java/org/jboss/as/weld/WeldRegisterBeansService.java

**New file** - Force CDI proxy creation at build time:

```java
public class WeldRegisterBeansService implements Service {
    
    @Override
    public void start(final StartContext context) throws StartException {
        if (!runOnce.compareAndSet(false, true)) {
            return;
        }
        ClassLoader oldTccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            for (SetupAction action : setupActions) {
                action.setup(null);
            }
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            BeanManager beanManager = beanManagerSupplier.get();
            Class[] classes = WildFlyGraalSetup.getCDIClasses();
            for (Class clazz : classes) {
                System.out.println("Force Proxies creation for " + clazz.getName());
                Bean<?> bean = beanManager.resolve(beanManager.getBeans(clazz));
                CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
                beanManager.getReference(bean, clazz, creationalContext);
                try {
                    Object obj = ContextualInstance.get(bean, ((BeanManagerProxy)beanManager).delegate(), creationalContext);
                } catch(Exception ex) {
                    // OK, attempt to create an instance that can be invalid for transient scopes (e.g.: request).
                    System.err.println(ex);
                }
            }
        } catch(Exception ex) {
            throw new StartException(ex);
        } finally {
            for (SetupAction action : setupActions) {
                try {
                    action.teardown(null);
                } catch (Exception e) {
                    WeldLogger.DEPLOYMENT_LOGGER.exceptionClearingThreadState(e);
                }
            }
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
        }
    }
}
```

**Why this is needed**:

CDI proxies must be generated at build time and frozen in the native image heap. GraalVM cannot generate new proxy classes at runtime. This service forces proxy creation for all discovered CDI beans during the build phase.

### weld/subsystem/src/main/java/org/jboss/as/weld/deployment/processors/WeldDeploymentCleanupProcessor.java

Register beans service at build time:

```java
if (WildFlyGraalSetup.isBuildTime()) {
    ServiceName weldBeanManagerServiceName = BeanManagerService.serviceName(deploymentUnit);
    ServiceName weldRegisterBeansServiceName = parent.getServiceName().append(WeldRegisterBeansService.SERVICE_NAME);
    ServiceBuilder<?> weldRegisterBeansServiceBuilder = serviceTarget.addService(weldRegisterBeansServiceName);
    final Supplier<BeanManager> beanManagerSupplier = weldRegisterBeansServiceBuilder.requires(weldBeanManagerServiceName);
    weldRegisterBeansServiceBuilder.requires(weldStartCompletionServiceName);
    weldRegisterBeansServiceBuilder.setInstance(new WeldRegisterBeansService(beanManagerSupplier, WeldDeploymentProcessor.getSetupActions(deploymentUnit), module.getClassLoader()));
    weldRegisterBeansServiceBuilder.install();
}
```

**Why this is needed**:

Installs the `WeldRegisterBeansService` to force proxy creation at build time, after Weld startup is complete.

### weld/subsystem/src/main/java/org/jboss/as/weld/services/bootstrap/WeldExecutorServices.java

Implemented passivate/activate lifecycle (commit c4b2276ff7 fixed NPE):

```java
private ThreadFactory factory;

@Override
public void start(final StartContext context) throws StartException {
    factory = new JBossThreadFactory(null, Boolean.FALSE, null, THREAD_NAME_PATTERN, null, null);
    this.executor = new WeldExecutor(bound, runnable -> {
        Thread thread = factory.newThread(runnable);
        if (WildFlySecurityManager.isChecking()) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    thread.setContextClassLoader(null);
                    return null;
                }
            });
        } else {
            thread.setContextClassLoader(null);
        }
        return thread;
    });
    // ...
}

@Override
public void passivate() {
    if (executor != null) {  // NPE fix
        executor.shutdownNow();
    }
}

@Override
public void activate() {
    this.executor = new WeldExecutor(bound, runnable -> {
        Thread thread = factory.newThread(runnable);
        if (WildFlySecurityManager.isChecking()) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    thread.setContextClassLoader(null);
                    return null;
                }
            });
        } else {
            thread.setContextClassLoader(null);
        }
        return thread;
    });
}
```

**Why this is needed**:

Executor threads cannot run at build time. Threads must be shut down before native image compilation (passivate) and recreated at runtime (activate). The NPE check prevents crashes when passivate is called before the executor is initialized.

## Module: pom.xml (Root Project Dependencies)

### pom.xml

Updated to GraalVM-compatible versions:

```xml
<version.org.jboss.resteasy>6.2.16.Final-SNAPSHOT</version.org.jboss.resteasy>
<version.org.wildfly.core>31.0.0.Beta3-SNAPSHOT</version.org.wildfly.core>
```

**Why this is needed**:

These versions contain GraalVM-specific modifications from the `resteasy` and `wildfly-core` projects.

## Key Concepts

### 1. Passivate/Activate Lifecycle

Many services implement a new lifecycle for GraalVM native images:

- **Build time**: `start()` initializes services, then `passivate()` cleans up resources (close threads, sockets, pools)
- **Native image compilation**: State is frozen in heap
- **Runtime**: `activate()` re-creates runtime resources (threads, sockets, pools)

This pattern appears in:
- `ByteBufferPoolService`
- `ListenerService` 
- `ConsoleRedirectService`
- `WeldExecutorServices`

### 2. Deferred Initialization via Suppliers

Services that cannot run at build time use the supplier pattern:

- **Build time**: Store a `Supplier<T>` instead of `T`
- **Runtime**: Call `supplier.get()` to obtain the actual instance

This pattern appears in:
- `XnioWorker` → `XnioWorkerSupplier`
- WebSocket worker initialization

### 3. Build-time Detection

Code uses `WildFlyGraalSetup.isBuildTime()` to conditionally skip operations at build time:

```java
if (WildFlyGraalSetup.isBuildTime()) {
    // Store context, skip execution
    return;
}
// Normal runtime execution
```

### 4. CDI Proxy Pre-generation

CDI proxies must be generated at build time because:
1. GraalVM cannot generate new proxy classes at runtime
2. All proxy bytecode must be frozen in the native image heap
3. `WeldRegisterBeansService` forces proxy creation for all discovered beans

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    Build Time Phase                       │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  1. WildFly starts in suspended mode                      │
│     ├─ Services initialize but don't start                │
│     ├─ Listeners don't bind to sockets                    │
│     ├─ Threads don't start                                │
│     └─ Pools/workers remain uninitialized                 │
│                                                            │
│  2. CDI beans discovered                                  │
│     ├─ WeldRegisterBeansService forces proxy creation     │
│     ├─ All CDI proxies generated and frozen in heap       │
│     └─ Bean metadata cached                               │
│                                                            │
│  3. Services passivate                                    │
│     ├─ ByteBufferPool closed and nulled                   │
│     ├─ Executor threads shut down                         │
│     ├─ XnioWorker deferred via supplier                   │
│     └─ Network listeners store context but don't start    │
│                                                            │
│  4. Native image compilation                              │
│     └─ All state frozen in heap                           │
│                                                            │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│                    Runtime Phase                          │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  1. Services activate                                     │
│     ├─ ByteBufferPool re-created                          │
│     ├─ Executor threads started                           │
│     ├─ XnioWorker obtained from supplier                  │
│     └─ Network listeners bind to sockets                  │
│                                                            │
│  2. Server ready                                          │
│     ├─ CDI proxies loaded from heap (no generation)       │
│     ├─ HTTP listeners accept connections                  │
│     ├─ WebSockets use lazy-initialized workers            │
│     └─ Startup complete in 10-15ms                        │
│                                                            │
└──────────────────────────────────────────────────────────┘
```

## Integration with Other Projects

WildFly integrates GraalVM modifications from:

1. **wildfly-core** (`31.0.0.Beta3-SNAPSHOT`): Core server infrastructure with GraalVM support
2. **resteasy** (`6.2.16.Final-SNAPSHOT`): JAX-RS implementation with GraalVM modifications
3. **undertow**: HTTP server with deferred initialization (modifications in this repo)
4. **wildfly-elytron**: Security subsystem with GraalVM support
5. **jboss-modules**: Module system with ServiceLoader cache
6. **weld**: CDI implementation with proxy pre-generation

All these projects contribute to the full native image capability of WildFly.
