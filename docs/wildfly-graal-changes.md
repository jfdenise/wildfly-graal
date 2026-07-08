# WildFly-Graal - GraalVM Native Image Support

## Summary

The `wildfly-graal` project enables WildFly application server to run as a GraalVM native image with drastically improved startup time and memory footprint:

**Performance improvements:**
- **Startup time**: 10-15ms vs 2000ms in Java (200x faster)
- **Memory (RSS)**: 8MB vs 28MB in Java (3.5x reduction)

**Key components:**
1. **runtime**: Core runtime utilities for build-time/runtime detection and caching
2. **module-launcher**: Native image entry point that pre-loads modules and services
3. **wildfly-substitutions**: GraalVM substitutions for JBoss Modules ServiceLoader
4. **analyzer**: Deployment analysis tooling to discover classes and resources
5. **build-app-image**: Container image builder for native WildFly applications
6. **builder-image**: Pre-built builder image with tooling and dependencies
7. **demo**: Example deployments demonstrating various features
8. **deployment-src**: Source code for demo deployments
9. **files**: Configuration files for native image build

## Overview

**Approach**: 
1. Start WildFly in suspended mode at build time
2. Passivate services and cleanup resources
3. Freeze server state in the native image heap
4. At runtime, activate services from frozen state

## Module: runtime/

### src/main/java/org/wildfly/graal/runtime/WildFlyGraalSetup.java

**Central runtime utility class for GraalVM integration (515 lines).**

This class provides:
1. **Build-time/runtime detection**: `isBuildTime()`, `isRuntime()` 
2. **Cache management**: `GraalCache` for storing proxies and arbitrary data
3. **Reflection caching**: Pre-cache constructors, methods, annotations
4. **ServiceLoader support**: Cache service implementations at build time
5. **Permission handling**: Serialize/deserialize permissions (meaningless pre-JDK25)
6. **JBoss Modules integration**: Access to ModuleClassLoader cache APIs

**Key methods:**

```java
// Build-time/runtime detection
public static boolean isBuildTime()    // Returns true during native image build
public static boolean isRuntime()      // Returns true when running in native image

// Cache management
public static GraalCache initCache(String key)
public static GraalCache getCache(String key)

// Reflection caching - store at build time, retrieve at runtime
public static void addClassToCache(ClassLoader loader, String className, Class<?>... params)
public static Constructor getConstructorFromCache(ClassLoader loader, Class<?> clazz, Class<?>... params)
public static Class<?> getClassFromCache(ClassLoader loader, String className)
public static <T extends Annotation> T getAnnotation(ClassLoader loader, Class<?> clazz, Class<T> annotationClass)
public static Method[] getDeclaredMethods(ClassLoader loader, Class<?> clazz)
public static Constructor[] getConstructors(ClassLoader loader, Class<?> clazz)

// Convenience methods
public static <T> T newInstance(Class<T> collectionType)  // Create instance using cached constructor
public static Class[] getCDIClasses()                       // Get list of CDI bean classes
public static Class[] getJsonBindingEagerClasses()          // Get list of JSON-B classes

// Permission handling (meaningless pre-JDK25)
public static void buildtimeStaticInitEnded()  // Cleanup permissions at end of static init
public static void runtimeStarted()             // Set runtime flag
```

**GraalCache inner class:**

```java
public static class GraalCache {
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final Map<Class, Object> proxies = new ConcurrentHashMap<>();
    
    public void add(String key, Object value)
    public Object get(String key)
    public void addProxy(Class itf, Object value)
    public Object getProxy(Class itf)
}
```

**Why this is needed:**

In GraalVM native images:
1. **Reflection is restricted** - must be configured at build time
2. **Class loading is frozen** - all classes must be discovered during build
3. **Dynamic proxies must be pre-generated** - cannot create new proxy classes at runtime
4. **Resources must be registered** - cannot load arbitrary resources at runtime

`WildFlyGraalSetup` provides a caching layer that:
- Captures reflection metadata at build time
- Stores it in the native image heap
- Provides runtime access without reflection

**JBoss Modules Integration:**

The class uses reflection to access JBoss Modules internal APIs:

```java
static {
    if (Boolean.getBoolean("org.wildfly.graal.build.time")) {
        buildtime = true;
        module = Class.forName("org.jboss.modules.Module", ...);
        moduleClassLoader = Class.forName("org.jboss.modules.ModuleClassLoader", ...);
        cache = Class.forName("org.jboss.modules.ClassCache", ...);
        
        getCache = module.getMethod("getCache");
        getModule = moduleClassLoader.getMethod("getModule");
        addClassToCache = cache.getMethod("addClassToCache", String.class);
        getConstructorFromCache = cache.getMethod("getConstructorFromCache", ...);
        // ... more method handles
    }
}
```

This allows the runtime module to work with JBoss Modules' caching infrastructure added specifically for GraalVM support.

## Module: module-launcher/

### src/main/java/org/wildfly/graal/launcher/Launcher.java

**Native image entry point that initializes WildFly at build time.**

**Build-time initialization:**

```java
static {
    // 1. Load deployment class and resource lists
    List<String> allDeploymentClasses = new ArrayList<>();
    List<String> allResources = new ArrayList<>();
    
    Path deploymentClasses = Paths.get("analyzer-output/allDeploymentClasses.txt");
    if (Files.exists(deploymentClasses)) {
        allDeploymentClasses.addAll(Files.readAllLines(deploymentClasses));
        allDeploymentClasses.addAll(DEPLOYMENT_WELL_KNOWN_CLASSES);
    }
    
    Path resourcesPath = Paths.get("analyzer-output/resources.txt");
    if (Files.exists(resourcesPath)) {
        allResources.addAll(Files.readAllLines(resourcesPath));
    }
    
    WildFlyGraalSetup.setDeploymentSetup(allDeploymentClasses, allResources, Cache.class);
    
    // 2. Setup module loader
    Path modulesDir = Paths.get(JBOSS_HOME + "/modules").toAbsolutePath();
    LocalModuleLoader loader = (LocalModuleLoader) setupModuleLoader(modulesDir.toString());
    
    // 3. Load all modules and pre-populate ServiceLoader caches
    for (String moduleName : allModules.keySet()) {
        Module mod = loader.loadModule(moduleName);
        Cache classCache = new Cache();
        mod.setClassCache(classCache);
        
        if (moduleName.equals("org.jboss.as.standalone")) {
            mainModule = mod;
        }
        
        // Pre-load all services for this module
        for (String serviceClass : mod.getServices()) {
            Set<String> servicesImpl = mod.getCache().addServiceToCache(serviceClass);
            // Service implementations are now cached
        }
    }
    
    // 4. Start WildFly in suspended mode (build time only)
    mainModule.preMain(args);  // Initializes WildFly but doesn't start listeners
}

public static void main(String[] args) throws Exception {
    // At runtime, resume WildFly from suspended state
    mainModule.run(args);
}
```

**Well-known deployment classes:**

```java
private static final List<String> DEPLOYMENT_WELL_KNOWN_CLASSES = new ArrayList<>();

static {
    // Classes loaded at runtime that need pre-cached constructors
    DEPLOYMENT_WELL_KNOWN_CLASSES.add("com.fasterxml.jackson.databind.type.TypeFactory");
    DEPLOYMENT_WELL_KNOWN_CLASSES.add("java.util.ArrayList");
    DEPLOYMENT_WELL_KNOWN_CLASSES.add("java.util.TreeSet");
    DEPLOYMENT_WELL_KNOWN_CLASSES.add("java.util.HashSet");
}
```

**Why this is needed:**

The launcher performs a two-phase initialization:

**Phase 1 - Build time (`static` block):**
1. Load all JBoss Modules
2. Pre-populate `ServiceLoader` caches for all modules
3. Start WildFly in suspended mode (services initialized but not started)
4. All state is frozen in the native image heap

**Phase 2 - Runtime (`main` method):**
1. Resume WildFly from suspended state
2. Activate services (open sockets, start threads, etc.)
3. Server is ready in 10-15ms

### src/main/java/org/wildfly/graal/launcher/Cache.java

**Implementation of `org.jboss.modules.ClassCache` interface.**

Provides caching for:
- Classes
- Constructors
- Methods
- Annotations
- Resources
- ServiceLoader implementations

The cache is populated at build time and accessed at runtime to avoid reflection.

## Module: wildfly-substitutions/

### src/main/java/org/wildfly/graal/substitutions/substitute_ServiceLoader.java

**GraalVM substitution for `java.util.ServiceLoader` to use pre-cached service implementations.**

```java
@TargetClass(className = "java.util.ServiceLoader")
public final class substitute_ServiceLoader {
    
    @Substitute()
    Iterator<?> iterator() {
        if (loader instanceof ModuleClassLoader) {
            ModuleClassLoader cl = (ModuleClassLoader) loader;
            
            // Retrieve pre-cached service implementations from module cache
            List<Object> lst = cl.getModule().getCache().getServicesFromCache(service);
            return lst == null ? new ArrayList<>().iterator() : lst.iterator();
        } else {
            // Fallback to default ServiceLoader behavior for non-module classloaders
            return new Iterator() {
                // ... standard ServiceLoader iteration logic
            };
        }
    }
    
    @Substitute
    public Stream<ServiceLoader.Provider> stream() {
        if (loader instanceof ModuleClassLoader) {
            ModuleClassLoader cl = (ModuleClassLoader) loader;
            
            // Retrieve pre-cached service implementations
            List<Object> lst = cl.getModule().getCache().getServicesFromCache(service);
            List<ServiceLoader.Provider> providers = new ArrayList<>();
            
            if (lst != null) {
                for (Object obj : lst) {
                    providers.add(new MyProvider(obj.getClass(), obj));
                }
            }
            return providers.stream();
        } else {
            // Fallback to default ServiceLoader behavior
            // ...
        }
    }
}
```

**Why this is needed:**

`ServiceLoader` normally discovers service implementations at runtime by:
1. Reading `META-INF/services/<interface>` files
2. Using reflection to instantiate provider classes

In GraalVM:
1. Resource reading must be configured at build time
2. Reflection must be configured at build time
3. Service provider classes must be registered for reflection

The substitution bypasses `ServiceLoader`'s discovery mechanism and returns pre-instantiated service providers from the module cache, populated during build time by `Launcher`.

### src/main/java/org/wildfly/graal/substitutions/substitute_ModuleClassLoader.java

**Currently commented out.** This was an experimental substitution for `ModuleClassLoader.findClass()` to use cached classes directly. Not currently used.

## Module: analyzer/

### Purpose

Analyzes WAR deployments to discover:
1. All classes referenced by the deployment
2. All resources (JSPs, web.xml, etc.)
3. CDI beans
4. JAX-RS endpoints
5. JSON-B classes

### Output

Creates text files in `analyzer-output/`:
- `allDeploymentClasses.txt` - All deployment classes
- `resources.txt` - All deployment resources

These files are consumed by `Launcher` during native image build.

### Key classes

- `Analyzer.java` - Main analyzer entry point
- `DeploymentScanner.java` - Scans WAR structure
- `DirectoryIndexer.java` - Indexes exploded deployments
- `NestedWarOrExplodedArchiveFileVisitor.java` - WAR file visitor

## Module: build-app-image/

### Purpose

Builds a container image containing the native WildFly executable with a deployed application.

### Process

1. Takes a WAR file as input
2. Runs the analyzer to discover deployment classes/resources
3. Provisions a WildFly server
4. Deploys the application
5. Runs custom CLI scripts (optional)
6. Compiles to native image using GraalVM
7. Packages into a container image

### Script

`build-wildfly-native-app-image.sh` - Main build script

**Usage:**
```bash
./build-wildfly-native-app-image.sh -d <war-file> -c <cli-script> -b <bash-script> -g ssl -a <custom-module.jar>
```

## Module: builder-image/

### Purpose

Pre-built container image containing:
1. Modified WildFly with GraalVM support
2. GraalVM native-image compiler
3. Build tooling (Maven, etc.)
4. All dependencies

### Availability

Pre-built images on quay.io:
- Mac ARM: `quay.io/jdenise/wildfly-graal-image-builder:latest`
- Linux x64: `quay.io/jdenise/wildfly-graal-image-builder-linux:latest`

### Dockerfile

`builder-image/Dockerfile` - Automation to build the builder image

## Module: demo/

### Contains

Example deployments demonstrating:
- JSP
- Servlets
- WebSockets
- JAX-RS
- JSON-B
- RESTEasy JSAPI
- WildFly CLI
- Elytron security
- SSL/TLS

### Configuration

- `user-script.cli` - Example CLI script for customization
- `user-script.sh` - Example bash script for build-time customization

## Module: deployment-src/

### Contains

Source code for demo deployments:
- `helloworld/` - Main demo with all features
- `ee-security/` - CDI + EE security demo
- `custom-module/` - Example custom Elytron auth module

## Module: files/

### Contains

Configuration files for native image build:
- Reflection configuration
- Resource configuration
- JNI configuration
- Proxy configuration

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Native Image Build                    │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  1. Analyzer scans WAR deployment                        │
│     ├─ Discovers all classes                             │
│     ├─ Discovers all resources                           │
│     ├─ Identifies CDI beans                              │
│     └─ Identifies JAX-RS endpoints                       │
│                                                           │
│  2. Launcher.static {} block executes                    │
│     ├─ Loads deployment class/resource lists             │
│     ├─ Loads all JBoss Modules                           │
│     ├─ Pre-populates ServiceLoader caches                │
│     ├─ Starts WildFly in suspended mode                  │
│     │  ├─ All services initialized                       │
│     │  ├─ CDI proxies generated                          │
│     │  ├─ JSON-B metadata cached                         │
│     │  └─ Reflection metadata cached                     │
│     └─ State frozen in heap                              │
│                                                           │
│  3. GraalVM compiles to native image                     │
│     └─ Frozen heap included in executable                │
│                                                           │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    Native Image Runtime                   │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  1. Launcher.main() executes                             │
│     └─ mainModule.run(args)                              │
│                                                           │
│  2. WildFly resumes from suspended state                 │
│     ├─ Services activate                                 │
│     │  ├─ HTTP listeners bind to sockets                 │
│     │  ├─ Thread pools start                             │
│     │  └─ XnioWorker initialized                         │
│     └─ Server ready in 10-15ms                           │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

## Key Concepts

### 1. Build-time vs Runtime

**Build time:** During native image compilation
- `WildFlyGraalSetup.isBuildTime()` returns true
- Reflection is allowed
- All classes/resources must be discovered
- State is frozen in the heap

**Runtime:** When native image executes
- `WildFlyGraalSetup.isRuntime()` returns true
- Reflection is restricted (uses pre-cached metadata)
- Classes/resources loaded from frozen heap
- State is restored from frozen heap

### 2. Suspended Mode

WildFly starts in "suspended mode" at build time:
- All subsystems initialize
- Services are created but not started
- No network sockets opened
- No threads started
- State is passivated (cleaned up for freezing)

At runtime:
- State is activated
- Services start
- Network sockets open
- Threads start

### 3. Caching Strategy

**Classes:** 
- Build time: Discover all classes, add to module cache
- Runtime: Load from cache instead of bytecode

**Constructors/Methods:**
- Build time: Cache Constructor/Method objects
- Runtime: Retrieve from cache instead of reflection

**Services:**
- Build time: Instantiate all ServiceLoader providers, cache instances
- Runtime: Return cached instances (ServiceLoader substitution)

**Proxies:**
- Build time: Generate all CDI/JAX-RS proxies
- Runtime: Reuse frozen proxy classes

**JSON-B:**
- Build time: Parse all JSON-B metadata with eager parsing
- Runtime: Use cached metadata

## Supported Features

✅ Servlets  
✅ JSP (with pre-compilation)  
✅ WebSockets  
✅ JAX-RS (RESTEasy)  
✅ JSON-B (Yasson)  
⚠️ CDI (Weld) - **Simple use cases only** (see limitations below)  
✅ Elytron Security  
✅ SSL/TLS  
✅ WildFly CLI  
✅ RESTEasy JSAPI  
❌ Bean Validation (validation of CDI proxy classes not supported)

## Limitations

1. **CDI (Weld)**: 
   - **Supported**: Simple CDI beans, injection, scopes (RequestScoped, SessionScoped, ApplicationScoped)
   - **Not supported**: Complex CDI features requiring runtime proxy generation for dynamically discovered beans
   - Bean proxies must be pre-generated at build time for all discovered CDI beans

2. **Bean Validation**: 
   - Validation of regular classes works
   - Validation of CDI proxy classes requires runtime reflection that cannot be pre-cached
   - Attempting to validate CDI proxies may fail

3. **RESTEASY_PROXY_IMPLEMENT_ALL_INTERFACES**: Not supported (proxies are pre-generated at build time with fixed interfaces)

4. **Dynamic module loading**: All modules must be loaded at build time

5. **Resource changes**: Resources are frozen at build time, cannot be modified at runtime
