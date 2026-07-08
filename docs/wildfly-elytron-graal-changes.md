# WildFly Elytron - GraalVM Modifications

## Summary

The modifications enable WildFly Elytron security providers to work in a GraalVM native image context:

**Build Time**:
- Provider instances created during WildFly startup
- Service registration triggers class caching
- All service implementation classes loaded and constructors cached
- Provider instances and cached metadata frozen in native image heap

**Runtime**:
- Provider instances already exist (loaded from heap)
- Service lookup returns existing `ProviderService` objects
- Service instantiation uses cached constructors instead of reflection
- Falls back to reflection if cache misses (non-Graal scenario)

The pattern delegates to `WildFlyGraalSetup` utility methods which interface with the JBoss Modules `ClassCache`:
- `WildFlyGraalSetup.addClassToCache()` - Registers class for caching (build time only)
- `WildFlyGraalSetup.getClassFromCache()` - Retrieves cached class (runtime only)
- `WildFlyGraalSetup.getConstructorFromCache()` - Retrieves cached constructor (runtime only)

This eliminates runtime reflection for Java security provider service instantiation, which is essential for GraalVM native images.

## Overview

**Tag**: `2026_july_end_phase2`  
**Total Changes**: 4 files modified, 240 lines added, 172 lines removed

## Changes

### 1. WildFlyElytronBaseProvider.java

Modified security service registration to use cache-aware `ProviderService`:

```java
// Before:
putService(new Service(this, PASSWORD_FACTORY_TYPE, "clear", 
    "org.wildfly.security.password.impl.PasswordFactorySpiImpl", emptyList, emptyMap));

// After:
putService(new ProviderService(this, PASSWORD_FACTORY_TYPE, "clear", 
    "org.wildfly.security.password.impl.PasswordFactorySpiImpl", emptyList, emptyMap, false, false));
```

Modified `ProviderService` class to cache and retrieve security service implementations:

#### Constructor registers class in cache:

```java
protected static class ProviderService extends Service {
    private final boolean withProvider;
    private final boolean reUsable;

    ProviderService(Provider provider, String type, String algorithm, String className, 
                    List<String> aliases, Map<String, String> attributes, 
                    boolean withProvider, boolean reUsable) {
        super(provider, type, algorithm, className, aliases, attributes);
        this.withProvider = withProvider;
        this.reUsable = reUsable;
        // Register class for caching at build time
        WildFlyGraalSetup.addClassToCache(WildFlyElytronBaseProvider.class.getClassLoader(), className);
    }
}
```

#### Class loading uses cache:

```java
private Class<?> getImplementationClass() throws NoSuchAlgorithmException {
    if (implementationClass == null) {
        ClassLoader classLoader = WildFlyElytronBaseProvider.class.getClassLoader();
        try {
            // Try cache first
            implementationClass = WildFlyGraalSetup.getClassFromCache(classLoader, getClassName());
            if (implementationClass == null) {
                // Fall back to standard loading
                implementationClass = Class.forName(getClassName(), false, classLoader);
            }
        } catch (ClassNotFoundException e) {
            throw log.noSuchAlgorithmCreateService(getType(), getAlgorithm(), e);
        }
    }
    return implementationClass;
}
```

#### Constructor lookup uses cache:

```java
private Constructor getConstructorFromCache(Class constructorParameter) throws NoSuchAlgorithmException {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    return WildFlyGraalSetup.getConstructorFromCache(loader, getImplementationClass(), constructorParameter);
}
```

#### Service instantiation uses cached constructors:

```java
private Object newInstanceFromCache(Object constructorParameter) throws NoSuchAlgorithmException {
    Constructor ctr = getConstructorFromCache(constructorParameter == null ? null : constructorParameter.getClass());
    if (ctr == null) {
        return super.newInstance(constructorParameter);
    } else {
        try {
            if (constructorParameter == null) {
                return ctr.newInstance();
            } else {
                return ctr.newInstance(constructorParameter);
            }
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            throw log.noSuchAlgorithmCreateService(getType(), getAlgorithm(), e);
        }
    }
}

private Object newInstanceWithProvider() throws NoSuchAlgorithmException {
    Class<?> implementationClass = getImplementationClass();
    
    try {
        Constructor<?> constructor = getConstructorFromCache(Provider.class);
        if (constructor == null) {
            constructor = implementationClass.getConstructor(Provider.class);
        }
        return constructor.newInstance(getProvider());
    } catch (Exception e) {
        throw log.noSuchAlgorithmCreateService(getType(), getAlgorithm(), e);
    }
}
```

#### Modified newInstance to use cache:

```java
@Override
public Object newInstance(Object constructorParameter) throws NoSuchAlgorithmException {
    if (reUsable) {
        Object response = instance == null ? null : instance.get();
        if (response == null) {
            synchronized(this) {
                instance = this.instance;
                if (instance == null || (response = instance.get()) == null) {
                    response = withProvider ? newInstanceWithProvider() : newInstanceFromCache(constructorParameter);
                    this.instance = new SoftReference<Object>(response);
                }
            }
        }
        return response;
    }
    
    return withProvider ? newInstanceWithProvider() : newInstanceFromCache(constructorParameter);
}
```

### 2. WildFlyElytronProvider.java

Applied same pattern as `WildFlyElytronBaseProvider` to all service registrations.

### 3. pom.xml files

Added `wildfly-graal-runtime` dependency:

```xml
<dependency>
    <groupId>org.wildfly.graal</groupId>
    <artifactId>wildfly-graal-runtime</artifactId>
    <version>${version.org.wildfly.graal}</version>
    <scope>provided</scope>
</dependency>
```

## Sequence

### Build Time

1. **Provider Loading** (during WildFly server startup in suspended mode):
   
   **Location**: `wildfly-core/elytron/src/main/java/org/wildfly/extension/elytron/ProviderDefinitions.java:254-268`
   
   ```java
   loadedProviders = new ArrayList<>();
   Iterable<Provider> providers = Module.findServices(Provider.class, 
       new Predicate<Class<?>>() {
           public boolean test(final Class<?> providerClass) {
               // Only pick up services from JBoss Modules
               return providerClass.getClassLoader() instanceof ModuleClassLoader;
           }
       }, classLoader);
   Iterator<Provider> iterator = providers.iterator();
   while (iterator.hasNext()) {
       final Provider p = iterator.next();
       loadedProviders.add(p);
   }
   ```
   
   - Uses `Module.findServices()` which calls JBoss Modules' ServiceLoader
   - Discovers `WildFlyElytronProvider` via `@MetaInfServices(Provider.class)` annotation
   - Instantiates the provider by calling its no-arg constructor

2. **Provider Constructor Execution**:
   ```java
   public WildFlyElytronProvider() {
       super("WildFlyElytron", "1.0", "WildFly Elytron Provider");
       putHttpAuthenticationMechanismImplementations();
       putKeyStoreImplementations();
       putPasswordImplementations();  // Registers hundreds of services
       putSaslMechanismImplementations();
       putCredentialStoreProviderImplementations();
       putAlgorithmParametersImplementations();
       // ...
   }
   ```

3. **Service Registration** (for each `putService()` call):
   ```java
   putService(new ProviderService(this, PASSWORD_FACTORY_TYPE, "bcrypt", 
       "org.wildfly.security.password.impl.PasswordFactorySpiImpl", ...));
   ```

4. **ProviderService Constructor**:
   ```java
   ProviderService(..., String className, ...) {
       super(...);
       // At BUILD TIME: Register class in cache
       WildFlyGraalSetup.addClassToCache(WildFlyElytronBaseProvider.class.getClassLoader(), className);
   }
   ```
   - This calls through to the JBoss Modules `ClassCache` for the `org.wildfly.security.elytron` module
   - The cache loads the class and stores its constructors

5. **Cache Population**:
   - Each service implementation class is loaded: `PasswordFactorySpiImpl`, `SaltedPasswordAlgorithmParametersSpiImpl`, etc.
   - Constructors are discovered and cached
   - Classes remain in the native image heap

5. **Provider Array Stored**: Providers returned as `Provider[]` array and stored in service

6. **Provider Registration at Runtime**:

   **Location**: `wildfly-core/elytron/src/main/java/org/wildfly/extension/elytron/ProviderRegistrationService.java:94-101`
   
   ```java
   /**
    * During the start, the security provider services classes and constructors have been cached.
    * The Security providers are empty at startup (java.security.Security class initialized at runtime, 
    * so build time init is lost).
    * We need to add the security providers at runtime.
    */
   @Override
   public void activate() throws StartException {
       start(null);  // Registers providers with java.security.Security
   }
   ```
   
   - At runtime, MSC service activation calls `activate()`
   - This calls `Security.insertProviderAt()` or `Security.addProvider()` (lines 59, 71)
   - Provider instances from build time are registered with Java's Security framework

### Runtime

1. **Application Requests Security Service** (e.g., password verification):
   ```java
   PasswordFactory factory = PasswordFactory.getInstance("bcrypt");
   ```

2. **Java Security Framework**:
   - Looks up registered providers (registered by `ProviderRegistrationService.activate()`)
   - `WildFlyElytronProvider` instance exists in native image heap
   - Finds matching service by algorithm name

3. **Service Instantiation**:
   ```java
   service.newInstance(constructorParameter)
   ```

4. **ProviderService.newInstance()**:
   ```java
   private Object newInstanceFromCache(Object constructorParameter) {
       // At RUNTIME: Retrieve from cache
       Constructor ctr = getConstructorFromCache(constructorParameter.getClass());
       if (ctr == null) {
           return super.newInstance(constructorParameter);  // Fallback
       }
       // Use cached constructor - no reflection needed
       return ctr.newInstance(constructorParameter);
   }
   ```

5. **Cache Lookup**:
   - `WildFlyGraalSetup.getConstructorFromCache()` retrieves cached constructor
   - Constructor already exists in native image - no reflection needed
   - Instance created directly
