# WildFly-GraalVM Architecture Documentation

## Overview

This document provides a comprehensive architectural overview of the WildFly-GraalVM integration, which enables WildFly application server to run as a GraalVM native image.

**Performance Results:**
- **Startup Time**: 10-15ms (vs 2000ms in standard Java) - **200x faster**
- **Memory Footprint**: 8MB RSS (vs 28MB in standard Java) - **3.5x reduction**

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Component Documentation](#component-documentation)
3. [Build-Time vs Runtime Phases](#build-time-vs-runtime-phases)
4. [Data Flow](#data-flow)
5. [Key Concepts](#key-concepts)
6. [Integration Points](#integration-points)

---

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Application Layer                           │
│  ┌──────────┬──────────┬────────────┬──────────┬──────────────────┐ │
│  │ Servlets │   JSPs   │ WebSockets │  JAX-RS  │  CDI Beans       │ │
│  └──────────┴──────────┴────────────┴──────────┴──────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                                  │
┌─────────────────────────────────────────────────────────────────────┐
│                        WildFly Subsystems                            │
│  ┌──────────┬──────────┬────────────┬──────────┬──────────────────┐ │
│  │ Undertow │   Weld   │   EE       │ Elytron  │  RESTEasy        │ │
│  └──────────┴──────────┴────────────┴──────────┴──────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                                  │
┌─────────────────────────────────────────────────────────────────────┐
│                      Core Infrastructure                             │
│  ┌───────────────┬──────────────┬─────────────┬──────────────────┐  │
│  │ JBoss Modules │  JBoss MSC   │  XNIO       │  Undertow Core   │  │
│  └───────────────┴──────────────┴─────────────┴──────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                                  │
┌─────────────────────────────────────────────────────────────────────┐
│                    WildFly-GraalVM Runtime                           │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  WildFlyGraalSetup (runtime utilities & caching)             │   │
│  │  - Build-time/runtime detection                              │   │
│  │  - Reflection caching                                         │   │
│  │  - ServiceLoader support                                      │   │
│  │  - Proxy caching                                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                  │
┌─────────────────────────────────────────────────────────────────────┐
│                          GraalVM Native Image                        │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  - Ahead-of-Time Compilation                                 │   │
│  │  - Closed-World Assumption                                    │   │
│  │  - Heap Snapshotting                                          │   │
│  │  - ServiceLoader Substitution                                 │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Component Documentation

### Modified Projects

Each of the following projects has been modified to support GraalVM native image compilation. Click the links to view detailed documentation for each component:

#### 1. Core Infrastructure

- **[JBoss Modules](./jboss-modules-graal-changes.md)**
  - Class caching infrastructure
  - Resource caching
  - ServiceLoader caching
  - Permission serialization (pre-JDK25)
  - Module passivation/activation lifecycle

- **[JBoss MSC](./jboss-msc-graal-changes.md)**
  - Service passivation/activation
  - Build-time service initialization
  - Runtime service resumption

- **[JBoss VFS](./jboss-vfs-graal-changes.md)**
  - Virtual file system caching
  - Resource mounting at build time

#### 2. WildFly Core

- **[WildFly Core](./wildfly-core-graal-changes.md)**
  - Server lifecycle management
  - Deployment processors
  - Management subsystem
  - I/O subsystem (XNIO integration)
  - Remoting subsystem
  - Elytron integration

- **[WildFly Elytron](./wildfly-elytron-graal-changes.md)**
  - Security providers passivation/activation
  - SSL context management
  - Credential store handling

#### 3. WildFly Subsystems

- **[WildFly (Main)](./wildfly-graal-changes.md)**
  - EE subsystem (class loading)
  - Undertow subsystem (HTTP server, listeners, byte buffers)
  - Weld subsystem (CDI proxy generation, bean validation)
  - Module dependencies

#### 4. Web and REST

- **[Undertow](./undertow-graal-changes.md)**
  - SecureRandom session ID generation

- **[RESTEasy](./resteasy-graal-changes.md)**
  - JavaScript API caching
  - JSON-B provider initialization
  - CDI injection target caching
  - Context proxy caching
  - XML document provider lazy initialization

#### 5. WildFly-GraalVM Integration

- **[WildFly-Graal Project](./wildfly-graal-changes.md)**
  - Runtime utilities (`WildFlyGraalSetup`)
  - Module launcher (native image entry point)
  - ServiceLoader substitutions
  - Deployment analyzer
  - Build tooling

---

## Build-Time vs Runtime Phases

### Build-Time Phase

```
┌───────────────────────────────────────────────────────────────┐
│                    1. Deployment Analysis                      │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  Analyzer scans WAR:                                   │   │
│  │  - Discover all classes (JAX-RS, CDI, servlets, etc.) │   │
│  │  - Discover all resources (JSPs, web.xml, etc.)       │   │
│  │  - Identify CDI beans                                  │   │
│  │  - Identify JAX-RS endpoints                           │   │
│  │  Output: allDeploymentClasses.txt, resources.txt      │   │
│  └────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│              2. Module Launcher Static Initialization          │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  Launcher static block:                                │   │
│  │  a) Load all JBoss Modules                             │   │
│  │  b) Create Cache for each module                       │   │
│  │  c) Pre-populate ServiceLoader caches                  │   │
│  │     - Instantiate all service providers                │   │
│  │     - Cache instances in module cache                  │   │
│  │  d) Start WildFly in suspended mode                    │   │
│  │     - mainModule.preMain(args)                         │   │
│  └────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│            3. WildFly Subsystem Initialization                 │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  Each subsystem initializes:                           │   │
│  │                                                         │   │
│  │  EE Subsystem:                                         │   │
│  │  - Cache class loading via ModuleClassLoader           │   │
│  │                                                         │   │
│  │  Undertow Subsystem:                                   │   │
│  │  - Create listeners (not started)                      │   │
│  │  - Create byte buffer pools (not started)              │   │
│  │  - Store StartContext for later activation             │   │
│  │                                                         │   │
│  │  Weld Subsystem:                                       │   │
│  │  - Start CDI container                                 │   │
│  │  - WeldRegisterBeansService runs:                      │   │
│  │    - Force proxy generation for all CDI beans          │   │
│  │    - Force bean validation metadata creation           │   │
│  │  - Create executor (not started)                       │   │
│  │                                                         │   │
│  │  RESTEasy:                                             │   │
│  │  - Pre-create PropertyInjectors                        │   │
│  │  - Cache JavaScript resources                          │   │
│  │  - Initialize JSON-B with eager class parsing          │   │
│  │  - Pre-create JAX-RS context proxies                   │   │
│  │                                                         │   │
│  │  Elytron:                                              │   │
│  │  - Load security providers (not started)               │   │
│  │  - Store provider state for later activation           │   │
│  │                                                         │   │
│  │  XNIO:                                                 │   │
│  │  - Create XnioWorker builder (not started)             │   │
│  └────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                 4. Service Passivation                         │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  Services implement Activatable interface:             │   │
│  │  - passivate() called on all services                  │   │
│  │  - Clean up resources that can't be frozen:            │   │
│  │    - Thread pools → null                               │   │
│  │    - Network listeners → null                          │   │
│  │    - Byte buffers → null                               │   │
│  │    - XNIO workers → null                               │   │
│  │    - Security providers → null                         │   │
│  │  - Keep configuration and builder state                │   │
│  └────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                5. GraalVM Native Image Build                   │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  GraalVM compiles to native executable:                │   │
│  │  - All classes reachable from Launcher.main()          │   │
│  │  - Heap snapshot of all initialized objects:           │   │
│  │    - All loaded modules                                │   │
│  │    - All cached classes/constructors/methods           │   │
│  │    - All cached service instances                      │   │
│  │    - All CDI proxies                                   │   │
│  │    - All JAX-RS proxies                                │   │
│  │    - All JSON-B metadata                               │   │
│  │    - Service configuration (not instances)             │   │
│  │  - ServiceLoader substitution included                 │   │
│  │  Result: single native executable (~145MB)             │   │
│  └────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
```

### Runtime Phase

```
┌───────────────────────────────────────────────────────────────┐
│                    1. Native Image Starts                      │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  OS loads native executable                            │   │
│  │  Heap snapshot restored                                │   │
│  │  All cached data available immediately                 │   │
│  │  WildFlyGraalSetup.runtimeStarted() called             │   │
│  └────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                 2. Launcher.main() Executes                    │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  mainModule.run(args)                                  │   │
│  │  - Resumes WildFly from suspended state                │   │
│  └────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                  3. Service Activation                         │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  activate() called on all services:                    │   │
│  │                                                         │   │
│  │  Undertow:                                             │   │
│  │  - Create byte buffer pools                            │   │
│  │  - Start HTTP listeners (bind to ports)                │   │
│  │                                                         │   │
│  │  Weld:                                                 │   │
│  │  - Create executor thread pool                         │   │
│  │  - CDI container ready (proxies already created)       │   │
│  │                                                         │   │
│  │  XNIO:                                                 │   │
│  │  - Create XnioWorker from builder                      │   │
│  │  - Start worker thread pool                            │   │
│  │                                                         │   │
│  │  Elytron:                                              │   │
│  │  - Restore security providers                          │   │
│  │  - Activate SSL contexts                               │   │
│  │                                                         │   │
│  │  RESTEasy:                                             │   │
│  │  - Use cached PropertyInjectors                        │   │
│  │  - Use cached JSON-B instance                          │   │
│  │  - Use cached JAX-RS proxies                           │   │
│  └────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                4. Server Ready (10-15ms)                       │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  WildFly accepting requests:                           │   │
│  │  - HTTP listeners on port 8080                         │   │
│  │  - Management on port 9990                             │   │
│  │  - HTTPS on port 8443 (if configured)                  │   │
│  └────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
```

---

## Data Flow

### ServiceLoader Resolution Flow

```
Runtime Request: ServiceLoader.load(XnioProvider.class)
                            │
                            ↓
┌───────────────────────────────────────────────────────────────┐
│          ServiceLoader Substitution intercepts                 │
└───────────────────────────────────────────────────────────────┘
                            │
                            ↓
                    Is ModuleClassLoader?
                    ┌──────┴──────┐
                   YES            NO
                    │              │
                    ↓              ↓
    Get module.getCache()    Use default
    .getServicesFromCache()  ServiceLoader
    (XnioProvider.class)
                    │
                    ↓
    Return cached instances
    (created at build time)
```

### Class Loading Flow

```
Runtime Request: classLoader.loadClass("com.example.MyClass")
                            │
                            ↓
                    Is ModuleClassLoader?
                    ┌──────┴──────┐
                   YES            NO
                    │              │
                    ↓              ↓
    Get module.getCache()    Use default
    .getClassFromCache()     ClassLoader
    ("com.example.MyClass")
                    │
                    ↓
    Return cached Class object
    (loaded at build time)
```

### Constructor Instantiation Flow

```
Runtime Request: new ArrayList<>()  (via RESTEasy StringParameterInjector)
                            │
                            ↓
            WildFlyGraalSetup.newInstance(ArrayList.class)
                            │
                            ↓
            getConstructorFromCache(loader, ArrayList.class)
                            │
                            ↓
            Get module.getCache().getConstructorFromCache()
                            │
                            ↓
            Return cached Constructor object
            (discovered at build time)
                            │
                            ↓
            constructor.newInstance()
```

---

## Key Concepts

### 1. Suspended Mode

**Definition:** A special mode where WildFly subsystems are initialized but services are not started.

**Build-time (Suspended):**
- Services created but passivated
- No network sockets opened
- No threads started
- No native resource allocation
- State prepared for freezing

**Runtime (Resumed):**
- Services activated from passivated state
- Network sockets opened
- Thread pools started
- Native resources allocated

### 2. Passivation/Activation Lifecycle

**Interface:**
```java
public interface Activatable {
    void passivate();  // Called at build-time end
    void activate();   // Called at runtime start
}
```

**Pattern:**
```java
class MyService implements Service, Activatable {
    private XnioWorker.Builder workerBuilder;  // Keep builder
    private XnioWorker worker;                 // Null at build time
    private StartContext context;              // Save for later
    
    @Override
    public void start(StartContext ctx) {
        if (isBuildTime()) {
            this.context = ctx;  // Save, don't start
            return;
        }
        worker = workerBuilder.build();  // Actually start
    }
    
    @Override
    public void passivate() {
        worker = null;  // Clean up
    }
    
    @Override
    public void activate() {
        start(context);  // Resume
    }
}
```

### 3. Caching Strategy

| What          | Build Time                          | Runtime                    |
|---------------|-------------------------------------|----------------------------|
| Classes       | Load all, add to module cache       | Return from cache          |
| Constructors  | Discover all, cache Constructor obj | Return from cache          |
| Methods       | Discover all, cache Method obj      | Return from cache          |
| Annotations   | Reflect all, cache Annotation obj   | Return from cache          |
| Services      | Instantiate all, cache instances    | Return instances (subst.)  |
| CDI Proxies   | Generate all proxies                | Use frozen proxy classes   |
| JAX-RS Ctx    | Generate all context proxies        | Use frozen proxy instances |
| JSON-B Meta   | Parse with eager-parse-classes      | Use cached metadata        |
| Resources     | Read all, cache content             | Return cached content      |

### 4. Closed-World Assumption

GraalVM requires all code reachable at runtime to be known at build time:

**Implications:**
- All classes must be discovered during analysis
- All reflection must be configured in advance
- All resources must be registered
- All dynamic proxies must be pre-generated
- No new classes can be loaded at runtime
- No new proxies can be created at runtime

**How WildFly-Graal handles this:**
1. **Deployment Analyzer** discovers all deployment classes
2. **Module Launcher** loads all JBoss Modules
3. **ServiceLoader caching** instantiates all service providers
4. **CDI WeldRegisterBeansService** generates all bean proxies
5. **RESTEasy** pre-creates all JAX-RS context proxies
6. **JSON-B** eager-parses all JSON-bindable classes

---

## Integration Points

### 1. JBoss Modules ↔ WildFly-Graal Runtime

```
┌─────────────────────────┐         ┌──────────────────────────┐
│    JBoss Modules        │         │  WildFly-Graal Runtime   │
│                         │         │                          │
│  Module                 │◄────────│  WildFlyGraalSetup       │
│  - getCache()           │         │  - addClassToCache()     │
│  - setClassCache()      │         │  - getConstructorFrom... │
│  - getServices()        │         │  - getClassFromCache()   │
│  - cleanupPermissions() │         │  - newInstance()         │
│  - restorePermissions() │         │  - getCDIClasses()       │
│                         │         │                          │
│  ClassCache             │◄────────│  Cache (implementation)  │
│  - addClassToCache()    │         │  - ServiceLoader cache   │
│  - getConstructorFrom...│         │  - Reflection cache      │
│  - addServiceToCache()  │         │  - Resource cache        │
└─────────────────────────┘         └──────────────────────────┘
```

### 2. WildFly Subsystems ↔ WildFly-Graal Runtime

```
┌─────────────────────────┐         ┌──────────────────────────┐
│  WildFly Subsystems     │         │  WildFly-Graal Runtime   │
│                         │         │                          │
│  - UndertowSubsystemAdd │────────►│  isBuildTime()           │
│  - ListenerService      │────────►│  isRuntime()             │
│  - WeldRegisterBeans... │────────►│  getCDIClasses()         │
│  - ByteBufferPool...    │         │                          │
│  - EEDefaultPermissions │         │                          │
│  - WarStructure...      │         │                          │
└─────────────────────────┘         └──────────────────────────┘
```

### 3. RESTEasy ↔ WildFly-Graal Runtime

```
┌─────────────────────────┐         ┌──────────────────────────┐
│       RESTEasy          │         │  WildFly-Graal Runtime   │
│                         │         │                          │
│  JSAPIWriter            │────────►│  GraalCache              │
│  - static {} caches JS  │         │  - add/get resources     │
│                         │         │                          │
│  AbstractJsonBinding... │────────►│  getJsonBindingEager...  │
│  - eager class parsing  │         │                          │
│                         │         │                          │
│  JaxrsInjectionTarget   │────────►│  isBuildTime()           │
│  - pre-create injector  │         │                          │
│                         │         │                          │
│  ContextParameterInj... │────────►│  GraalCache.proxies      │
│  - cache JAX-RS proxies │         │  - add/get proxies       │
│                         │         │                          │
│  StringParameterInj...  │────────►│  newInstance()           │
│  - instantiate collect. │         │  - cached constructors   │
└─────────────────────────┘         └──────────────────────────┘
```

### 4. GraalVM ↔ WildFly-Graal Substitutions

```
┌─────────────────────────┐         ┌──────────────────────────┐
│      GraalVM            │         │  Substitutions           │
│                         │         │                          │
│  ServiceLoader          │◄────────│  substitute_Service...   │
│  - iterator()           │ REPLACE │  - Check ModuleClass...  │
│  - stream()             │         │  - Return cached svcs    │
│                         │         │                          │
│  ModuleClassLoader      │         │  substitute_Module...    │
│  - findClass() [unused] │         │  - [commented out]       │
└─────────────────────────┘         └──────────────────────────┘
```

---

## Detailed Component Interactions

### Example: HTTP Request Processing

```
1. HTTP Request arrives at port 8080
   │
   ↓
2. Undertow ListenerService (activated at runtime)
   - Uses XnioWorker (created at runtime from builder)
   - Uses ByteBufferPool (created at runtime)
   │
   ↓
3. Dispatches to Servlet/JAX-RS
   │
   ↓
4. RESTEasy PropertyInjector
   - Uses cached injector (created at build time)
   │
   ↓
5. JAX-RS Context Injection (@Context UriInfo)
   - Returns cached proxy (created at build time)
   - Proxy delegates to runtime context data
   │
   ↓
6. CDI Bean Injection
   - Uses cached CDI proxy (created at build time by WeldRegisterBeansService)
   - Proxy delegates to bean instance
   │
   ↓
7. JSON-B Serialization
   - Uses cached Jsonb instance (created at build time with eager parsing)
   - Metadata already cached
   │
   ↓
8. Response written via Undertow
   - ByteBufferPool allocates buffer
   - Written to socket
```

### Example: ServiceLoader Resolution

```
1. Code calls: ServiceLoader.load(XnioProvider.class)
   │
   ↓
2. ServiceLoader substitution intercepts
   - Checks: loader instanceof ModuleClassLoader?
   │
   ↓
3. Get module cache
   - module = ((ModuleClassLoader) loader).getModule()
   - cache = module.getCache()
   │
   ↓
4. Retrieve cached services
   - services = cache.getServicesFromCache(XnioProvider.class)
   - Returns List<Object> of pre-instantiated providers
   │
   ↓
5. Return iterator/stream
   - Already instantiated at build time by Launcher
   - No reflection, no resource loading
```

---

## Summary

The WildFly-GraalVM integration achieves dramatic performance improvements through:

1. **Two-Phase Initialization**: Build-time initialization + runtime activation
2. **Comprehensive Caching**: Classes, constructors, methods, annotations, services, proxies
3. **Suspended Mode**: Initialize without starting to enable heap snapshotting
4. **ServiceLoader Substitution**: Replace dynamic discovery with pre-cached instances
5. **Passivation/Activation**: Clean lifecycle for resource management

For detailed implementation specifics, refer to the individual component documentation linked in the [Component Documentation](#component-documentation) section.

---

## Document Index

- [Architecture (this document)](./ARCHITECTURE.md)
- [JBoss Modules](./jboss-modules-graal-changes.md)
- [JBoss MSC](./jboss-msc-graal-changes.md)
- [JBoss VFS](./jboss-vfs-graal-changes.md)
- [WildFly Core](./wildfly-core-graal-changes.md)
- [WildFly Elytron](./wildfly-elytron-graal-changes.md)
- [WildFly Subsystems](./wildfly-graal-changes.md)
- [Undertow](./undertow-graal-changes.md)
- [RESTEasy](./resteasy-graal-changes.md)
- [WildFly-Graal Project](./wildfly-graal-changes.md)
