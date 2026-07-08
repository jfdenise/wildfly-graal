# RESTEasy - GraalVM Modifications

## Summary

The RESTEasy modifications enable JAX-RS REST services to work in a GraalVM native image context:

1. **resteasy-jsapi**: Cached JavaScript client resource at build time
2. **providers/json-binding**: Pre-initialized JSON-B provider with eager class parsing
3. **resteasy-cdi**: Pre-created property injectors at build time
4. **resteasy-core**: Cached context proxies, used cached constructors for collections, lazy-initialized XML document factories

## Overview

**Total Changes**: 7 files modified

## Module: resteasy-jsapi/

### src/main/java/org/jboss/resteasy/jsapi/JSAPIWriter.java

Cached the JavaScript client resource at build time:

```java
private static final String CLIENT_SCRIPT = "/resteasy-client.js";

static {
    try {
        if (WildFlyGraalSetup.isBuildTime()) {
            StringWriter stringWriter = new StringWriter();
            try (PrintWriter writer = new PrintWriter(new BufferedWriter(stringWriter))) {
                copyResource(CLIENT_SCRIPT, writer);
                writer.flush();
            }
            WildFlyGraalSetup.GraalCache cache = WildFlyGraalSetup.initCache(JSAPIWriter.class.getName());
            cache.add(CLIENT_SCRIPT, stringWriter.toString());
        }
    } catch (Exception ex) {
        throw new RuntimeException(ex);
    }
}

private static void copyResource(String name, PrintWriter writer) throws IOException {
    if (WildFlyGraalSetup.isRuntime()) {
        WildFlyGraalSetup.GraalCache cache = WildFlyGraalSetup.getCache(JSAPIWriter.class.getName());
        String content = (String) cache.get(name);
        writer.write(content);
    } else {
        Reader reader = new InputStreamReader(JSAPIWriter.class.getResourceAsStream(name));
        char[] array = new char[1024];
        int read;
        while ((read = reader.read(array)) >= 0) {
            writer.write(array, 0, read);
        }
        reader.close();
    }
}
```

**Why this is needed**:

The RESTEasy JavaScript API generates client code that includes a JavaScript library resource. In GraalVM:
1. **Build time**: Read the JavaScript resource file and cache it as a string
2. **Runtime**: Retrieve the cached string instead of reading from the resource file

This avoids resource loading issues at runtime in the native image.

## Module: providers/json-binding/

### src/main/java/org/jboss/resteasy/plugins/providers/jsonb/AbstractJsonBindingProvider.java

Pre-initialized JSON-B provider with eager class parsing:

```java
private static final Jsonb JSONB;

static {
    if (WildFlyGraalSetup.isBuildTime()) {
        Class[] classes = WildFlyGraalSetup.getJsonBindingEagerClasses();
        if (classes != null) {
            final JsonbConfig jsonbConfig = new JsonbConfig()
                    .setProperty("yasson.eager-parse-classes", classes);
            JSONB = JsonbBuilder.create(jsonbConfig);
        } else {
            JSONB = null;
        }
    } else {
        JSONB = null;
    }
}

protected Jsonb getJsonb(Class<?> type) {
    ContextResolver<Jsonb> contextResolver = providers.getContextResolver(Jsonb.class, MediaType.APPLICATION_JSON_TYPE);
    Jsonb delegate = null;
    if (contextResolver != null) {
        delegate = contextResolver.getContext(type);
    } else {
        if (WildFlyGraalSetup.isRuntime()) {
            delegate = JSONB;
        }
    }
    return new ManagedJsonb(delegate);
}
```

**Why this is needed**:

JSON-B (Yasson) performs reflection-based metadata discovery for JSON serialization/deserialization. In GraalVM:
1. **Build time**: Create a `Jsonb` instance with `yasson.eager-parse-classes` configuration
2. The eager parsing forces Yasson to discover and cache metadata for all JSON-bindable classes
3. **Runtime**: Use the pre-initialized `Jsonb` instance with cached metadata

Without this, Yasson would attempt runtime reflection which is restricted in native images.

## Module: resteasy-cdi/

### src/main/java/org/jboss/resteasy/cdi/JaxrsInjectionTarget.java

Pre-created property injector at build time:

```java
private PropertyInjector propertyInjector;

public JaxrsInjectionTarget(final InjectionTarget<T> delegate, final Class<T> clazz) {
    this.delegate = delegate;
    this.clazz = clazz;
    hasPostConstruct = Types.hasPostConstruct(clazz, validatePostConstructParameters);
    if (WildFlyGraalSetup.isBuildTime()) {
        propertyInjector = getPropertyInjector();
    }
}

@Override
public void inject(T instance, CreationalContext<T> ctx) {
    delegate.inject(instance, ctx);

    // We need to load PropertyInjector lazily since RESTEasy starts
    // after the CDI lifecycle events are executed
    if (propertyInjector == null) {
        propertyInjector = getPropertyInjector();
    }

    HttpRequest request = ResteasyContext.getContextData(HttpRequest.class);
    HttpResponse response = ResteasyContext.getContextData(HttpResponse.class);

    if ((request != null) && (response != null)) {
        propertyInjector.inject(request, response, instance, false);
    } else {
        propertyInjector.inject(instance, false);
    }
    // ...
}
```

**Why this is needed**:

`PropertyInjector` instances analyze JAX-RS resource classes for `@Context` and other injection points. Creating them at build time:
1. Allows reflection-based analysis to happen during build
2. The injector metadata is frozen in the heap
3. At runtime, the cached injector can inject values without re-analyzing the class

## Module: resteasy-core/

### src/main/java/org/jboss/resteasy/core/SynchronousDispatcher.java

Added static initialization block to pre-load LogMessages:

```java
{
    // This is to make sure LogMessages are preloaded as profiler shows a runtime hit
    // This will also insure that this initialization is done at static init time when loaded with Graal
    // Not a big deal if you remove this.
    @SuppressWarnings("unused")
    LogMessages preload = LogMessages.LOGGER;
}
```

Pre-loading logger messages at build time improves startup performance.

### src/main/java/org/jboss/resteasy/core/ContextParameterInjector.java

Cached JAX-RS context proxies at build time:

```java
private Object proxyInstance;

public ContextParameterInjector(final Class<?> proxy, final Class<?> rawType, final Type genericType,
        final Annotation[] annotations, final ResteasyProviderFactory factory) {
    this.rawType = rawType;
    this.genericType = genericType;
    this.proxy = proxy;
    this.factory = factory;
    this.annotations = annotations;
    if (WildFlyGraalSetup.isBuildTime()) {
        // Having a cache of proxy means that resteasy.proxy.implement.all.interfaces option is not supported with Graal
        proxyInstance = getProxyFromCache(rawType);
        if (proxyInstance == null) {
            Class[] itfs = new Class[1];
            itfs[0] = rawType;
            proxyInstance = Proxy.newProxyInstance(rawType.getClassLoader(), itfs, new GenericDelegatingProxy());
            addProxyToCache(rawType, proxyInstance);
        }
    }
}

protected Object createProxy() {
    if (proxyInstance != null) {
        return proxyInstance;
    }
    // ... fallback to runtime proxy creation
}

private static Object getProxyFromCache(Class<?> key) {
    Map<Class<?>, Object> map = ResteasyContext.getContextDataMap();
    WildFlyGraalSetup.GraalCache cache = (WildFlyGraalSetup.GraalCache) map.get(WildFlyGraalSetup.GraalCache.class);
    Object value = null;
    if (cache != null) {
        value = cache.getProxy(key);
    }
    return value;
}

private static void addProxyToCache(Class<?> key, Object value) {
    Map<Class<?>, Object> map = ResteasyContext.getContextDataMap();
    WildFlyGraalSetup.GraalCache cache = (WildFlyGraalSetup.GraalCache) map.get(WildFlyGraalSetup.GraalCache.class);
    if (cache != null) {
        cache.addProxy(key, value);
    }
}
```

**Why this is needed**:

RESTEasy creates dynamic proxies for JAX-RS context injection (`@Context UriInfo`, `@Context HttpHeaders`, etc.). In GraalVM:
1. **Build time**: Create proxies for all context types and cache them
2. Proxy classes are frozen in the heap
3. **Runtime**: Reuse cached proxy instances instead of creating new ones

**Note**: This means the `resteasy.proxy.implement.all.interfaces` option is not supported with GraalVM.

### src/main/java/org/jboss/resteasy/core/StringParameterInjector.java

Used cached constructors for collection instantiation:

```java
Collection collection = null;
try {
    collection = WildFlyGraalSetup.newInstance(collectionType);
} catch (Exception e) {
    throw new RuntimeException(e);
}
```

Instead of using `collectionType.newInstance()`, use `WildFlyGraalSetup.newInstance()` which retrieves constructors from a pre-built cache. This avoids reflection-based instantiation at runtime.

### src/main/java/org/jboss/resteasy/plugins/providers/DocumentProvider.java

Lazy-initialized XML document factories:

```java
private TransformerFactory transformerFactory;
private DocumentBuilderFactory documentBuilder;
private volatile boolean init;

public DocumentProvider() {
    // nullary constructor so that GraalVM's native-image is able to process this file
    this(ResteasyContext.getContextData(ResteasyConfiguration.class));
}

private void lazyInit() {
    if (!init) {
        synchronized (this) {
            if (!init) {
                this.documentBuilder = DocumentBuilderFactory.newInstance();
                this.transformerFactory = TransformerFactory.newInstance();
                this.init = true;
            }
        }
    }
}
```

**Why this is needed**:

XML factories (`DocumentBuilderFactory`, `TransformerFactory`) cannot be initialized at build time because they:
1. May load native libraries or platform-specific implementations
2. Have state that should not be frozen in the heap

The lazy initialization defers factory creation to runtime while maintaining a nullary constructor required by GraalVM's native-image processing.
