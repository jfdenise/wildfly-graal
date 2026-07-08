# JBoss VFS - GraalVM Modifications

## Summary

The modifications enable JBoss VFS to work in a GraalVM native image context:

1. **Resource Preservation**: Prevents cleanup of temporary directories at runtime (`TempDir`, `TempFileProvider`)
2. **Static Random Removal**: Eliminates static `Random` instances (`TempFileProvider`, `VirtualFileAssembly`)
3. **Runtime Mount Prevention**: Blocks new VFS mounts at runtime (`VFS.getMount()`)
4. **Passivation/Activation**: New `JavaZipFileSystemGraal` class with lifecycle methods for build-time/runtime transition
5. **Build-time Detection**: Uses `WildFlyGraalSetup` utility to distinguish build vs runtime phases

## Overview

**Tag**: `2026_july_end_phase2`  
**Total Changes**: 7 files modified, 268 lines added, 7 lines removed

## Changes

### 1. Dependency Addition (pom.xml)

Added `wildfly-graal-runtime` dependency to access `WildFlyGraalSetup` utility class for build-time vs runtime detection.

```xml
<dependency>
  <groupId>org.wildfly.graal</groupId>
  <artifactId>wildfly-graal-runtime</artifactId>
  <version>${version.org.wildfly.graal}</version>
  <scope>provided</scope>
</dependency>
```

### 2. TempDir.java

Modified `close()` method to skip cleanup at runtime:

```java
public void close() throws IOException {
    if (WildFlyGraalSetup.isRuntime()) {
        return;
    }
    if (open.getAndSet(false)) {
        provider.delete(root);
    }
}
```

### 3. TempFileProvider.java

Removed static `Random` field and made instance creation local:

```java
// Removed: private static final Random rng = new Random();

static String createTempName(String prefix, String suffix) {
    Random rng = new Random();
    return prefix + Long.toHexString(rng.nextLong()) + suffix;
}
```

Modified `close()` method to skip cleanup at runtime:

```java
public void close() throws IOException {
    if (WildFlyGraalSetup.isRuntime()) {
        return;
    }
    if (open.getAndSet(false)) {
        delete(this.providerRoot);
    }
}
```

### 4. VFS.java

Added runtime check to prevent new VFS mounts:

```java
static Mount getMount(VirtualFile virtualFile) {
    if (WildFlyGraalSetup.isRuntime()) {
        throw new RuntimeException("Can't access the VFS at runtime, to load " 
            + virtualFile + " all should already be loaded");
    }
    // ... rest of method
}
```

Use `JavaZipFileSystemGraal` at build time:

```java
// In mountZip(File zipFile, ...)
FileSystem fs = WildFlyGraalSetup.isBuildTime() 
    ? new JavaZipFileSystemGraal(zipFile, tempDir)
    : new JavaZipFileSystem(zipFile, tempDir);

// In mountZip(String zipName, InputStream zipData, ...)
FileSystem fs = WildFlyGraalSetup.isBuildTime() 
    ? new JavaZipFileSystemGraal(zipName, zipData, tempDir)
    : new JavaZipFileSystem(zipName, zipData, tempDir);
```

### 5. VirtualFileAssembly.java

Deferred `SecureRandom` initialization:

```java
private static final Random RANDOM_NUM_GEN;
static {
    if (WildFlyGraalSetup.isBuildTime()) {
        RANDOM_NUM_GEN = null;
    } else {
        RANDOM_NUM_GEN = new SecureRandom();
    }
}

private String getAssemblyId() {
    Random random = RANDOM_NUM_GEN;
    if (RANDOM_NUM_GEN == null) {
        random = new SecureRandom();
    }
    return Long.toHexString(random.nextLong());
}
```

### 6. JavaZipFileSystem.java

Changed class from `final` to extensible:

```java
// Before: public final class JavaZipFileSystem implements FileSystem
// After:
public class JavaZipFileSystem implements FileSystem {
```

### 7. JavaZipFileSystemGraal.java (New File - 224 lines)

New class implementing passivation/activation pattern.

Global registry for all instances:

```java
private static final List<JavaZipFileSystemGraal> ALL = new ArrayList<>();

public static void passivateFiles(TempFileProvider provider) throws IOException {
    for(JavaZipFileSystemGraal zip : ALL) {
        zip.passivate(provider);
    }
}

public static void activateFiles(TempFileProvider provider) throws IOException {
    for(JavaZipFileSystemGraal zip : ALL) {
        zip.activate(provider);
    }
}
```

Delegation pattern with passivation/activation:

```java
private JavaZipFileSystem delegate;
private final File archiveFile;
private TempFileProvider provider;
private final String name;

void passivate(TempFileProvider provider) throws IOException {
    delegate = null;
    this.provider = provider;
}

void activate(TempFileProvider provider) throws IOException {
    delegate = new JavaZipFileSystem(archiveFile, provider.createTempDir(name));
}
```

Lazy activation during compilation:

```java
private JavaZipFileSystem getFileSystem() {
    if (delegate == null) {
        if (!WildFlyGraalSetup.isBuildTime()) {
            throw new RuntimeException("Invalid state, zip file is null for " + archiveFile);
        }
        return new JavaZipFileSystem(archiveFile, provider.createTempDir(name));
    }
    return delegate;
}
```

All FileSystem interface methods delegate to `getFileSystem()`.
