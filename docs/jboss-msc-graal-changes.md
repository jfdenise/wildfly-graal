# JBoss MSC - GraalVM Modifications

## Summary

The modifications enable JBoss MSC to work in a GraalVM native image context:

1. **Service Tracking**: All installed services tracked at build time
2. **Executor Management**: Thread pool shut down at build time, recreated at runtime
3. **Passivation/Activation Lifecycle**: Services can prepare for heap freeze and resume at runtime
4. **Delayed Activation**: Specific services can be activated later in the startup sequence
5. **Classloader Context**: Each service activated with correct classloader
6. **MBean Disabled**: JMX registration disabled at build time

The key insight: **The entire service container graph exists in the heap**, including all service instances, their dependencies, and states. Services are running/started at build time, passivated before image compilation, then reactivated at runtime to resume operations.

## Overview

**Tag**: `2026_july_end_phase2`  
**Total Changes**: 9 files modified, 149 lines added, 144 lines removed

JBoss MSC (Modular Service Container) manages WildFly's service lifecycle. The modifications enable the service container and all services to be frozen in the native image heap at build time and reactivated at runtime.

## Changes

### 1. Service.java

Added lifecycle methods for passivation/activation:

```java
/**
 * Passivate the service at the end of the Graal VM build time phase.
 */
default void passivate() {
}

/**
 * Activate the service at the beginning of the Graal VM runtime phase.
 */
default void activate() throws StartException {
}
```

These are default methods - services can override to implement custom passivation/activation logic.

### 2. ServiceContainer.java

Added container-level passivation/activation methods:

```java
/**
 * To allow for services and container to be kept in the Graal VM heap.
 */
default void passivateServices() {}

/**
 * To activate the services and container when the Graal VM starts at runtime.
 * The delayPrefix is a way to delay some services at the end of the activation based on their name.
 */
default void activateServices(String delayPrefix) throws StartException {}
```

### 3. ServiceContainerImpl.java

#### Disabled MBean registration at build time:

```java
static {
    MBeanServer mBeanServer = null;
    if (WildFlyGraalSetup.isBuildTime()) {
        MBEAN_SERVER = null;
    } else {
        try {
            mBeanServer = ManagementFactory.getPlatformMBeanServer();
        } catch (final Exception e) {
            ServiceLogger.ROOT.mbeanServerNotAvailable(e);
        } finally {
            MBEAN_SERVER = mBeanServer;
        }
    }
}
```

#### Added tracking of services for passivation/activation:

```java
private final Set<ServiceName> serviceNames = new LinkedHashSet<>();
private final List<org.jboss.msc.Service> servicesToActivate = new ArrayList<>();
private final Map<org.jboss.msc.Service, ServiceToName = new HashMap<>();
```

#### Record services at build time:

```java
synchronized (registration) {
    registration.acquireWrite();
    if (WildFlyGraalSetup.isBuildTime()) {
        serviceNames.add(name);  // Track all installed services
    }
    // ...
}
```

#### Passivate services and shutdown executor:

```java
@Override
public void passivateServices() {
    Set<org.jboss.msc.Service> seenServices = Collections.newSetFromMap(new IdentityHashMap<>());
    for (ServiceName serviceName : serviceNames) {
        ServiceRegistrationImpl reg = registry.get(serviceName);
        org.jboss.msc.Service s = reg.getDependencyController().service;
        if (!seenServices.contains(s)) {
            seenServices.add(s);
            serviceToName.put(s, serviceName);
            s.passivate();  // Call service's passivate()
            servicesToActivate.add(s);
        }
    }
    executor.shutdownNow();  // Shutdown thread pool executor
}
```

Key aspects:
- Iterates through all installed services
- Uses `IdentityHashMap` to track unique service instances (same service may have multiple names)
- Calls `passivate()` on each service
- Stores services for later activation
- Shuts down the executor thread pool

#### Activate services at runtime:

```java
@Override
public void activateServices(String delayPrefix) throws StartException {
    executor = new ContainerExecutor(coreSize, coreSize, timeOut, timeOutUnit);
    List<org.jboss.msc.Service> delayed = new ArrayList<>();
    
    for(org.jboss.msc.Service s : servicesToActivate) {
        ClassLoader current = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(s.getClass().getClassLoader());
            ServiceName name = serviceToName.get(s);
            if (name.getCanonicalName().startsWith(delayPrefix)) {
                delayed.add(s);
            } else {
                s.activate();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(current);
        }
    }
    
    // Activate delayed services last
    for(org.jboss.msc.Service s : delayed) {
        s.activate();
    }
}
```

Key aspects:
- Recreates the executor thread pool
- Sets proper classloader context for each service activation
- Supports delayed activation for specific service name prefixes
- Activates delayed services last

#### Disabled container shutdown at build time:

```java
.setTerminationTask(new Runnable() {
    public void run() {
        if (WildFlyGraalSetup.isBuildTime()) {
            System.out.println("Disabling shutdown of JBoss MSC container at build time");
        } else {
            shutdownComplete(shutdownInitiated);
        }
    }
})
```

Prevents container shutdown when executor thread pool terminates at build time.

#### Store executor parameters for runtime recreation:

```java
private ContainerExecutor executor;
private final int coreSize;
private final long timeOut;
private final TimeUnit timeOutUnit;

ServiceContainerImpl(String name, int coreSize, long timeOut, TimeUnit timeOutUnit, final boolean autoShutdown) {
    // ...
    this.coreSize = coreSize;
    this.timeOut = timeOut;
    this.timeOutUnit = timeOutUnit;
    executor = new ContainerExecutor(coreSize, coreSize, timeOut, timeOutUnit);
    // ...
}
```

Changed `executor` from `final` to allow recreation at runtime.

### 4. WritableValueImpl.java and ServiceControllerImpl.java

Minor changes to support the lifecycle (not shown in detail).

## Sequence

### Build Time

1. **Service Installation**:
   - WildFly subsystems install hundreds of services into MSC container
   - Each service installation tracked in `serviceNames` set

2. **Service Startup**:
   - Services start and run in suspended mode
   - Container executor thread pool runs tasks

3. **Passivation** (called from `BootstrapImpl.passivateServices()`):
   ```java
   container.passivateServices();
   ```
   - Iterates all services, calling `service.passivate()` on each
   - Services can release resources, clear state, prepare for heap freeze
   - Stores service list for runtime activation
   - Shuts down executor thread pool
   - Container and services frozen in native image heap

### Runtime

1. **Activation** (called from `BootstrapImpl.finishBoot()`):
   ```java
   container.activateServices(delayPrefix);
   ```
   - Recreates executor thread pool
   - Iterates services from build time
   - Sets proper classloader for each service
   - Calls `service.activate()` on each (except delayed ones)
   - Calls `service.activate()` on delayed services last

2. **Service Operation**:
   - Services resume from suspended state
   - Container fully operational

## Integration with WildFly

The container passivation/activation is orchestrated in `wildfly-core/server/src/main/java/org/jboss/as/server/BootstrapImpl.java`:

**Build Time**:
```java
void passivateServices() {
    container.passivateServices();
}
```

**Runtime**:
```java
public void finishBoot(long startTime) throws ConfigurationPersistenceException {
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    applicationServerService.finishBoot(startTime);
}
```

The `finishBoot()` triggers service activation through the ApplicationServerService.
