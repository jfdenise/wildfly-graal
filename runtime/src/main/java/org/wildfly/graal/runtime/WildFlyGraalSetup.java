package org.wildfly.graal.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * This class must be initialized at build time.
 */
public class WildFlyGraalSetup {

    // Graal is only supported for WildFly in a modular context
    private static final boolean IS_MODULAR;
    private static final Class<?> MODULE_CACHE_HANDLER;
    private static final Class<?> MODULE_CACHE;
    private static final Class<?> MODULE;
    private static final Method GET_CACHE;
    private static final Method ADD_CLASS_TO_CACHE;
    private static final Method ADD_SERVICE_TO_CACHE;
    private static final Method GET_CONSTRUCTOR_FROM_CACHE;
    private static final Method GET_CLASS_FROM_CACHE;
    private static final Method SET_CACHE;
    private static final Method GET_SERVICES;
    private static final Method GET_ANNOTATION;
    private static final Method GET_METHOD_ANNOTATION;
    private static final Method GET_METHOD_PARAMETER_ANNOTATIONS;
    private static final Method GET_DECLARED_METHODS;
    private static final Method GET_METHOD;
    private static final Method RESTORE_PERMISSIONS;
    private static final Method CLEANUP_PERMISSIONS;
    private static final Map<String, List<java.security.Permission>> PERMISSIONS_CACHE = new HashMap<>();
    private static final Map<String, Field> IO_OPTIONS_FIELDS = new HashMap<>();
    private static boolean runtime;
    private static boolean buildtime;
    private static List<String> deploymentClasses;
    private static Class<?> cacheImpl;
    private static Object deploymentModule;
    private static Object deploymentReflectionIndex;
    private static Object scisMetaData;
    static {
        Class<?> cacheHandler = null;
        Class<?> cache = null;
        Class<?> module = null;
        Method getCache = null;
        Method addClassToCache = null;
        Method getConstructorFromCache = null;
        Method getClassFromCache = null;
        Method setCache = null;
        Method getServices = null;
        Method addServiceToCache = null;
        Method getAnnotation = null;
        Method getMethodAnnotation = null;
        Method getDeclaredMethods = null;
        Method getMethod = null;
        Method restorePermissions = null;
        Method cleanupPermissions = null;
        Method getMethodParameterAnnotations = null;
        boolean isModular = false;
        if (Boolean.getBoolean("org.wildfly.graal.build.time")) {
            buildtime = true;
            try {
                module = Class.forName("org.jboss.modules.Module", false, WildFlyGraalSetup.class.getClassLoader());
                cacheHandler = Class.forName("org.jboss.modules.CacheHandler", false, WildFlyGraalSetup.class.getClassLoader());
                cache = Class.forName("org.jboss.modules.ClassCache", false, WildFlyGraalSetup.class.getClassLoader());
                getCache = cacheHandler.getMethod("getCache");
                addClassToCache = cache.getMethod("addClassToCache", String.class);
                getConstructorFromCache = cache.getMethod("getConstructorFromCache", Class.class, Class[].class);
                getClassFromCache = cache.getMethod("getClassFromCache", String.class);
                getAnnotation = cache.getMethod("getAnnotation", Class.class, Class.class);
                getMethodAnnotation = cache.getMethod("getAnnotation", Class.class, Method.class, Class.class);
                getMethodParameterAnnotations = cache.getMethod("getParameterAnnotations", Class.class, Method.class);
                getDeclaredMethods = cache.getMethod("getDeclaredMethods", Class.class);
                getMethod = cache.getMethod("getMethod", Class.class, String.class, Class[].class);
                setCache = module.getMethod("setClassCache", cache);
                getServices = module.getMethod("getServices");
                restorePermissions = module.getMethod("restorePermissions");
                cleanupPermissions = module.getMethod("cleanupPermissions");
                addServiceToCache = cache.getMethod("addServiceToCache", String.class);
                System.out.println("WILDFLY GRAAL INITIALIZING GRAAL SETUP OK ");
                isModular = true;
            } catch (Exception ex) {
                System.out.println("WILDFLY GRAAL ERROR INITIALIZING GRAAL SETUP " + ex);
            }
        } else {
            System.out.println("WILDFLY NOT INITIALIZING GRAAL SETUP BECAUSE NOT AT BUILD TIME ");
        }
        IS_MODULAR = isModular;
        MODULE = module;
        MODULE_CACHE_HANDLER = cacheHandler;
        MODULE_CACHE = cache;
        GET_CACHE = getCache;
        ADD_CLASS_TO_CACHE = addClassToCache;
        GET_CONSTRUCTOR_FROM_CACHE = getConstructorFromCache;
        GET_CLASS_FROM_CACHE = getClassFromCache;
        GET_ANNOTATION = getAnnotation;
        GET_METHOD_ANNOTATION = getMethodAnnotation;
        GET_METHOD_PARAMETER_ANNOTATIONS = getMethodParameterAnnotations;
        GET_DECLARED_METHODS = getDeclaredMethods;
        GET_METHOD = getMethod;
        SET_CACHE = setCache;
        GET_SERVICES = getServices;
        ADD_SERVICE_TO_CACHE = addServiceToCache;
        RESTORE_PERMISSIONS = restorePermissions;
        CLEANUP_PERMISSIONS = cleanupPermissions;
    }

    public static void buildtimeStaticInitEnded() {
        try {
            CLEANUP_PERMISSIONS.invoke(deploymentModule);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void buildtimeEnded() {
        System.clearProperty("org.wildfly.graal.build.time");
        buildtime = false;
    }

    public static void runtimeStarted() {
        System.setProperty("org.wildfly.graal", "true");
        buildtime = false;
        runtime = true;
    }

    public static void setDeploymentSetup(List<String> classes, Class<?> clazz) {
        deploymentClasses = Collections.unmodifiableList(classes);
        cacheImpl = clazz;
    }

    private static boolean isModular() {
        return IS_MODULAR;
    }

    public static boolean isBuildTime() {
        return isModular() && buildtime;
    }

    public static boolean isRuntime() {
        return isModular() && runtime;
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
                    Object cache = GET_CACHE.invoke(loader);
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

    public static void setupDeploymentModule(Object module) {
        try {
            if (isBuildTime()) {
                if (MODULE.isAssignableFrom(module.getClass())) {
                    deploymentModule = module;
                    Object cache = cacheImpl.newInstance();
                    SET_CACHE.invoke(module, cache);
                    Set<String> services = (Set<String>) GET_SERVICES.invoke(module);
                    for (String service : services) {
                        if (!service.startsWith("java.lang.")) {
                            ADD_SERVICE_TO_CACHE.invoke(cache, service);
                        }
                    }
                    for (String depClass : deploymentClasses) {
                        ADD_CLASS_TO_CACHE.invoke(cache, depClass);
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Object getDeploymentModule() {
        try {
            if (isRuntime()) {
                RESTORE_PERMISSIONS.invoke(deploymentModule);
            }
            return deploymentModule;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void addPermission(java.security.Permission perm, String moduleName, String className) {
        if (isBuildTime()) {
            moduleName = moduleName == null ? "" : moduleName;
            List<java.security.Permission> lst = PERMISSIONS_CACHE.get(moduleName);
            if (lst == null) {
                lst = new ArrayList<>();
                PERMISSIONS_CACHE.put(moduleName, lst);
            }
            lst.add(perm);
        }
    }

    public static java.security.Permission getPermission(String moduleName, String className) {
        if (isRuntime()) {
            moduleName = moduleName == null ? "" : moduleName;
            List<java.security.Permission> lst = PERMISSIONS_CACHE.get(moduleName);
            for (java.security.Permission p : lst) {
                if (p.getClass().getName().equals(className)) {
                    return p;
                }
            }
            throw new RuntimeException("Permission " + className + " not found");
        }
        return null;
    }

    public static void cacheIoOptionField(String clazz, Field f) {
        if (isBuildTime()) {
            IO_OPTIONS_FIELDS.put(clazz, f);
        }
    }

    public static Field getIoOptionField(String className) {
        if (isRuntime()) {
            return IO_OPTIONS_FIELDS.get(className);
        }
        return null;
    }
    
    public static boolean isJMXRegistrationSupported() {
        if (isBuildTime()) {
            return false;
        }
        return true;
    }
    
    public static void setDeploymentReflectionIndex(Object index) {
        if (isBuildTime()) {
            deploymentReflectionIndex = index;
        }
    }
    public static Object getDeploymentReflectionIndex() {
        if (isRuntime()) {
            return deploymentReflectionIndex;
        }
        return null;
    }
    
    public static Annotation getAnnotation(Class<?> clazz, Class<? extends Annotation> annotType) {
        try {
            if (isRuntime()) {
                ClassLoader loader = clazz.getClassLoader();
                if (MODULE_CACHE_HANDLER.isAssignableFrom(loader.getClass())) {
                    Object cache = GET_CACHE.invoke(loader);
                    return (Annotation) GET_ANNOTATION.invoke(cache, clazz, annotType);
                }
                return null;
            } else {
                return clazz.getAnnotation(annotType);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Annotation getAnnotation(Class<?> clazz, Method m, Class<? extends Annotation> annotType) {
        try {
            if (isRuntime()) {
                ClassLoader loader = clazz.getClassLoader();
                if (MODULE_CACHE_HANDLER.isAssignableFrom(loader.getClass())) {
                    Object cache = GET_CACHE.invoke(loader);
                    return (Annotation) GET_METHOD_ANNOTATION.invoke(cache, clazz, m, annotType);
                }
                return null;
            } else {
                return m.getAnnotation(annotType);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Annotation getDeclaredAnnotation(Class<?> clazz, Class<? extends Annotation> annotType) {
        return getAnnotation(clazz, annotType);
    }

    public static Annotation getDeclaredAnnotation(Class<?> clazz, Method m, Class<? extends Annotation> annotType) {
        return getAnnotation(clazz, m, annotType);
    }

    public static boolean isAnnotationPresent(Class<?> clazz, Method m, Class<? extends Annotation> annotType) {
        if (isRuntime()) {
            return getAnnotation(clazz, m, annotType) != null;
        } else {
            return m.isAnnotationPresent(annotType);
        }
    }

    public static Annotation[][] getParameterAnnotations(Class<?> clazz, Method m) {
        try {
            if (isRuntime()) {
                ClassLoader loader = clazz.getClassLoader();
                if (MODULE_CACHE_HANDLER.isAssignableFrom(loader.getClass())) {
                    Object cache = GET_CACHE.invoke(loader);
                    return (Annotation[][]) GET_METHOD_PARAMETER_ANNOTATIONS.invoke(cache, clazz, m);
                }
                return null;
            } else {
                return m.getParameterAnnotations();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Method[] getDeclaredMethods(Class<?> clazz) {
        try {
            if (isRuntime()) {
                ClassLoader loader = clazz.getClassLoader();
                if (MODULE_CACHE_HANDLER.isAssignableFrom(loader.getClass())) {
                    Object cache = GET_CACHE.invoke(loader);
                    return (Method[]) GET_DECLARED_METHODS.invoke(cache, clazz);
                }
                return null;
            } else {
                return clazz.getDeclaredMethods();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Method getMethod(Class<?> clazz, String name, Class<?>... params) throws NoSuchMethodException {
        try {
            if (isRuntime()) {
                ClassLoader loader = clazz.getClassLoader();
                if (MODULE_CACHE_HANDLER.isAssignableFrom(loader.getClass())) {
                    Object cache = GET_CACHE.invoke(loader);
                    return (Method) GET_METHOD.invoke(cache, clazz, name, params);
                }
                return null;
            } else {
                return clazz.getMethod(name, params);
            }
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public static Object getScisMetaData() {
        if(isRuntime()) {
            return scisMetaData;
        }
        return null;
    }
    public static void setScisMetaData(Object obj) {
        if(isBuildTime()) {
            scisMetaData = obj;
        }
    }
}
