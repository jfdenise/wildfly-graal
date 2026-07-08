# Undertow - GraalVM Modifications

## Summary

The Undertow modifications enable the HTTP server to work in a GraalVM native image context:

1. **Session ID Generation**: Deferred SecureRandom initialization to runtime

## Overview

**Total Changes**: 1 file modified, 6 lines added, 1 line removed

## Module: core/

### core/src/main/java/io/undertow/server/session/SecureRandomSessionIdGenerator.java

Deferred SecureRandom initialization to runtime:

```java
private SecureRandom random;

public SecureRandomSessionIdGenerator() {
    if (!WildFlyGraalSetup.isBuildTime()) {
        random = new SecureRandom();
    }
}

@Override
public String createSessionId() {
    final byte[] bytes = new byte[length];
    (random == null ? new SecureRandom() : random).nextBytes(bytes);
    return new String(encode(bytes));
}
```

**Why this is needed**:

`SecureRandom` instances cannot be initialized at build time because:
1. They rely on native entropy sources that are not available during image build
2. The random state would be frozen in the heap, making all session IDs predictable
3. Each runtime instance must create its own entropy source

The fix:
- **Build time**: Skip `SecureRandom` initialization (`random` remains null)
- **Runtime**: Create `SecureRandom` on-demand when generating session IDs
- If `random` is null (first call after startup), create a new instance; otherwise use the cached instance
