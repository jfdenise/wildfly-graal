# JBoss Modules - GraalVM Modifications

## Summary

The modifications enable JBoss Modules to work in a GraalVM native image context:

1. **ClassCache Abstraction**: Provides pluggable caching for classes, constructors, methods, annotations, services, and resources
2. **Deferred Class Loading**: At runtime, classes are retrieved from cache instead of being loaded dynamically
3. **Service Discovery**: Build-time discovery of `META-INF/services` entries for caching
4. **JAR File Lazy Loading**: JAR files nulled at build time, recreated on-demand during compilation, reopened at runtime
5. **Two-Phase Execution**: 
   - `preRun()` called from GraalVM static initializer at build time - invokes application's `preMain()` method and caches main method handle
   - `run()` called at runtime - invokes cached main method handle
6. **Permission Serialization**: Protection domains and permissions serialized at build time, restored at runtime (this change is meaningless, done pre-JDK25 when permissions were still relevant)
7. **Resource Caching**: Resources loaded and cached at build time, served from cache at runtime

## Overview

**Tag**: `2026_july_end_phase2`  
**Total Changes**: 42 Java files modified, 1952 lines added, 1112 lines removed (excluding tests and infrastructure files)

## Integration with WildFly

The `wildfly-graal/module-launcher` project orchestrates the build process:

1. **Static Initializer** (build time):
   - Loads all JBoss modules
   - Attaches `Cache` instance to each module
   - Discovers and caches all services from `META-INF/services`
   - Calls `mainModule.preRun()` which invokes `org.jboss.as.server.Main.preMain()`
     - Starts WildFly server in suspended mode (`--start-mode=suspend`)
     - Allows server to initialize and load classes
     - Calls `impl.passivateServices()` to prepare for native image compilation
   - Calls `cleanupPermissions()` on all modules

2. **Main Method** (runtime):
   - Calls `mainModule.run()` which invokes cached `org.jboss.as.server.Main.main()`
     - Calls `impl.finishBoot()` to resume server from suspended state
     - Server becomes operational

## Changes

### 1. ClassCache.java (New File - 139 lines)

New abstract class providing a caching layer for reflection operations, class loading, and resource access.

Abstract methods for caching:

```java
public abstract void addClassToCache(String className) throws Exception;
public abstract Class<?> getClassFromCache(String className);
public abstract Constructor getConstructorFromCache(Class<?> clazz, Class<?>... parameterTypes);
public abstract Constructor[] getConstructors(Class<?> type);
public abstract Constructor[] getDeclaredConstructors(Class<?> type);
public abstract Set<String> addServiceToCache(String className) throws Exception;
public abstract List<Object> getServicesFromCache(Class<?> type);
public abstract Annotation getAnnotation(Class<?> clazz, Class<? extends Annotation> type);
public abstract Annotation getAnnotation(Class<?> clazz, Method m, Class<? extends Annotation> type);
public abstract Annotation[][] getParameterAnnotations(Class<?> clazz, Method m);
public abstract Method[] getDeclaredMethods(Class<?> clazz);
public abstract Method getMethod(Class<?> clazz, String name, Class<?>[] params) throws NoSuchMethodException;
public abstract void addResourceToCache(String path) throws IOException;
public abstract InputStream getResourceAsStream(String path) throws IOException;
```

Default implementation (no-op):

```java
static class DefaultClassCache extends ClassCache {
    // All methods return null or empty collections
    // Delegates directly to standard reflection/classloading
}
```

Module association:

```java
private Module module;

void setModule(Module module) {
    this.module = module;
}
```

### 2. Module.java

Added cache support:

```java
private ClassCache classCache;

public ClassCache getCache() {
    return classCache;
}

public void setClassCache(ClassCache classCache) {
    this.classCache = classCache;
    this.classCache.setModule(this);
}
```

Constructor initialization:

```java
Module(...) {
    // ... existing code
    classCache = ClassCache.DEFAULT;
}
```

Added `preRun()` method to invoke `preMain()` at build time:

```java
private Class<?> moduleMainClass;
private MethodHandle mainMethod;
private static final MethodType PRE_MAIN_METHOD_TYPE = MethodType.methodType(void.class, String[].class);

public void preRun(final String[] args) throws NoSuchMethodException, InvocationTargetException, ClassNotFoundException {
    preRun(mainClassName, args);
}

private void preRun(final String className, final String[] args) {
    moduleMainClass = Class.forName(className, false, moduleClassLoader);
    Class.forName(className, true, moduleClassLoader); // Initialize class
    
    final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    // Cache main method handle for runtime
    mainMethod = lookup.findStatic(moduleMainClass, "main", MAIN_METHOD_TYPE);
    
    // Invoke preMain method at build time
    MethodHandle preMainMethod = lookup.findStatic(moduleMainClass, "preMain", PRE_MAIN_METHOD_TYPE);
    preMainMethod.invokeExact(args);
}
```

Modified `run()` to use cached method handle at runtime:

```java
public void run(final String className, final String[] args) {
    if (mainMethod == null) {
        doRun(className, args); // Original implementation (non-Graal path)
    } else {
        mainMethod.invokeExact(args); // Use cached handle (Graal runtime)
    }
}
```

**Usage Pattern**:
- **Build time** (GraalVM static initializer): `mainModule.preRun(args)` 
  - Calls `preMain(args)` method on the main class
  - Starts WildFly server in suspended mode
  - Populates all caches
  - Caches the `main()` method handle
- **Runtime**: `mainModule.run(args)`
  - Invokes cached `main()` method handle
  - Resumes WildFly server from suspended state

Added permissions cleanup/restore for build-time passivation (this change is meaningless, done pre-JDK25 when permissions were still relevant):

```java
private List<PermissionData> permissions = new ArrayList<>();

private static class PermissionData {
    private String path;
    private String action;
    private Constructor constructor;
}

public void cleanupPermissions() throws Exception {
    if (this.permissionCollection != null) {
        Iterator<Permission> it = this.permissionCollection.elements().asIterator();
        while (it.hasNext()) {
            Permission p = it.next();
            PermissionData fpd = new PermissionData();
            fpd.action = p.getActions();
            fpd.path = p.getName();
            fpd.constructor = p.getClass().getConstructor(String.class, String.class);
            permissions.add(fpd);
        }
        permissionCollection = null;
    }
    getClassLoader().cleanupProtectionDomains();
}

public void restorePermissions() throws Exception {
    final Permissions perms = new Permissions();
    for(PermissionData p : permissions) {
        Permission pem = (Permission)p.constructor.newInstance(p.path, p.action);
        perms.add(pem);
    }
    permissionCollection = copyPermissions(perms);
    getClassLoader().restorePropectionDomain(permissionCollection);
}
```

Added service discovery method:

```java
public Set<String> getServices() throws ModuleLoadException {
    Set<String> services = new HashSet<>();
    Map<String, List<LocalLoader>> paths = getPaths();
    
    for(Entry<String, List<LocalLoader>> entry : paths.entrySet()) {
        if(entry.getKey().startsWith("META-INF/services")) {
            List<LocalLoader> l = paths.get(entry.getKey());
            for(LocalLoader loader : l) {
                if (loader instanceof IterableLocalLoader) {
                    IterableLocalLoader it = (IterableLocalLoader) loader;
                    Iterator<Resource> res = it.iterateResources("META-INF/services", false);
                    while(res.hasNext()) {
                        String name = res.next().getName();
                        name = name.substring(name.lastIndexOf("/")+1, name.length());
                        services.add(name);
                    }
                }
            }
        }
    }
    return services;
}
```

### 3. ModuleClassLoader.java

Added cache lookups in class loading:

```java
@Override
public Class<?> loadClass(final String className) throws ClassNotFoundException {
    if (Module.isRuntime()) {
        Class<?> clazz = getModule().getCache().getClassFromCache(className);
        if (clazz != null) {
            return clazz;
        }
    }
    return super.loadClass(className);
}

@Override
public Class<?> loadClass(final String className, boolean resolve) throws ClassNotFoundException {
    if (Module.isRuntime()) {
        Class<?> clazz = getModule().getCache().getClassFromCache(className);
        if (clazz != null) {
            return clazz;
        }
    }
    return super.loadClass(className, resolve);
}
```

Cache lookup in `findClass()`:

```java
protected final Class<?> findClass(String className, boolean exportsOnly, final boolean resolve) throws ClassNotFoundException {
    className = className.replace('/', '.');
    Class<?> loadedClass = findLoadedClass(className);
    if (loadedClass != null) {
        return loadedClass;
    }
    if (Module.isRuntime()) {
        Class<?> inCache = getModule().getCache().getClassFromCache(className);
        if (inCache != null) {
            return inCache;
        }
    }
    // ... continue with normal class loading
}
```

Cache lookup for resources:

```java
public final InputStream findResourceAsStream(final String name, boolean exportsOnly) {
    if (Module.isRuntime()) {
        InputStream inCache = getModule().getCache().getResourceAsStream(name);
        if (inCache != null) {
            return inCache;
        }
    }
    return module.getResourceAsStream(name);
}
```

Protection domain cleanup/restore:

```java
private final IdentityHashMap<CodeSource, ProtectionDomain> protectionDomains = new IdentityHashMap<>();

void cleanupProtectionDomains() {
    protectionDomains.clear();
}

void restorePropectionDomain(PermissionCollection permissions) {
    for(CodeSource codeSource : protectionDomains.keySet()) {
        ProtectionDomain protectionDomain = new ModularProtectionDomain(codeSource, permissions, this);
        protectionDomains.put(codeSource, protectionDomain);
    }
}
```

### 4. JarFileResourceLoader.java

Deferred JAR file opening:

```java
private JarFile getJarFile() {
    if (jarFile == null) {
        return buildJarFile();
    } else {
        return jarFile;
    }
}

private JarFile buildJarFile() {
    try {
        return new JarFile(fileOfJar, true, JarFile.OPEN_READ, JarFile.runtimeVersion());
    } catch (Exception ex) {
        throw new RuntimeException(ex);
    }
}
```

Constructor nulls JAR file at build time:

```java
JarFileResourceLoader(...) {
    fileOfJar = new File(jarFile.getName());
    if (!Module.isBuildTime()) {
        this.jarFile = jarFile;
    } else {
        this.jarFile = null;
    }
    // ... rest of initialization
}
```

All JAR file accesses go through `getJarFile()`:

```java
public synchronized ClassSpec getClassSpec(final String fileName) throws IOException {
    JarFile jarFile = getJarFile();
    final JarEntry entry = getJarEntry(jarFile, fileName);
    // ...
}

public PackageSpec getPackageSpec(final String name) throws IOException {
    JarFile jarFile = getJarFile();
    // ...
}

public Resource getResource(String name) {
    final JarFile jarFile = getJarFile();
    // ...
}
```

Null-safe close:

```java
public void close() throws IOException {
    try {
        super.close();
    } finally {
        if(jarFile != null) {
            jarFile.close();
        }
    }
}
```
