package org.wildfly.graal.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 *
 * This class must be initialized at build time.
 */
public class WildFlyGraalSetup {

    // Graal is only supported for WildFly in a modular context
    private static final Class<?> MODULE_CACHE_HANDLER;
    private static final Class<?> MODULE_CACHE;
    private static final Method GET_CACHE;
    private static final Method ADD_CLASS_TO_CACHE;
    private static final Method GET_CONSTRUCTOR_FROM_CACHE;
    private static final Method GET_CLASS_FROM_CACHE;

    static {
        Class<?> cacheHandler = null;
        Class<?> cache = null;
        Method getCache = null;
        Method addClassToCache = null;
        Method getConstructorFromCache = null;
        Method getClassFromCache = null;
        if (Boolean.getBoolean("org.wildfly.graal.build.time")) {
            try {
                cacheHandler = Class.forName("org.jboss.modules.CacheHandler", false, WildFlyGraalSetup.class.getClassLoader());
                cache = Class.forName("org.jboss.modules.ClassCache", false, WildFlyGraalSetup.class.getClassLoader());
                getCache = cacheHandler.getMethod("getCache");
                addClassToCache = cache.getMethod("addClassToCache", String.class);
                getConstructorFromCache = cache.getMethod("getConstructorFromCache", Class.class, Class[].class);
                getClassFromCache = cache.getMethod("getClassFromCache", String.class);
                System.out.println("WILDFLY ELYTRON INITIALIZING GRAAL SETUP OK ");
            } catch (Exception ex) {
                System.out.println("WILDFLY ELYTRON  ERROR INITIALIZING GRAAL SETUP " + ex);
            }
        } else {
            System.out.println("WILDFLY ELYTRON NOT INITIALIZING GRAAL SETUP BECAUSE NOT AT BUILD TIME ");
        }
        MODULE_CACHE_HANDLER = cacheHandler;
        MODULE_CACHE = cache;
        GET_CACHE = getCache;
        ADD_CLASS_TO_CACHE = addClassToCache;
        GET_CONSTRUCTOR_FROM_CACHE = getConstructorFromCache;
        GET_CLASS_FROM_CACHE = getClassFromCache;
    }

    private static boolean isModular() {
        return MODULE_CACHE_HANDLER != null
                && MODULE_CACHE != null
                && GET_CACHE != null
                && ADD_CLASS_TO_CACHE != null
                && GET_CONSTRUCTOR_FROM_CACHE != null
                && GET_CLASS_FROM_CACHE != null;
    }

    public static boolean isBuildTime() {
        return isModular() && Boolean.getBoolean("org.wildfly.graal.build.time");
    }

    public static boolean isRuntime() {
        return isModular() && Boolean.getBoolean("org.wildfly.graal");
    }

    public static void addClassToCache(ClassLoader loader, String className, Class<?>... params) {
        try {
            if (isBuildTime()) {
                if (MODULE_CACHE_HANDLER.isAssignableFrom(loader.getClass())) {
                    Object cache = GET_CACHE.invoke(loader);
                    ADD_CLASS_TO_CACHE.invoke(cache, className);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Constructor getConstructorFromCache(ClassLoader loader, Class<?> clazz, Class<?>... params) {
        try {
            if (isRuntime()) {
                if (MODULE_CACHE_HANDLER.isAssignableFrom(loader.getClass())) {
                    System.out.println("OK LOADED IS ASSIGNABLE");
                    Object cache = GET_CACHE.invoke(loader);
                    System.out.println("OK CACHE IS " + cache);
                    return (Constructor) GET_CONSTRUCTOR_FROM_CACHE.invoke(cache, clazz, params);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return null;
    }

    public static Class<?> getClassFromCache(ClassLoader loader, String className) {
        try {
            if (isRuntime()) {
                if (MODULE_CACHE_HANDLER.isAssignableFrom(loader.getClass())) {
                    Object cache = GET_CACHE.invoke(loader);
                    return (Class<?>) GET_CLASS_FROM_CACHE.invoke(cache, className);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return null;
    }
}
