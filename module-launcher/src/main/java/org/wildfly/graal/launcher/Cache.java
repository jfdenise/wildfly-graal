package org.wildfly.graal.launcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.jboss.logging.Logger;
import org.jboss.modules.ClassCache;
import org.jboss.modules.ModuleClassLoader;

/**
 *
 * @author jdenise
 */
public class Cache extends ClassCache {

    Logger LOGGER = Logger.getLogger("org.wildfly.graal");

    private final Map<String, Class<?>> CACHE = new HashMap<>();
    private final Map<Class<?>, List<Object>> SERVICES = new HashMap<>();
    private final Map<String, Constructor> CONSTRUCTORS = new HashMap<>();
    private static final Map<Class, Constructor[]> ALL_CONSTRUCTORS = new HashMap<>();
    private static final Map<Class, Constructor[]> ALL_DECLARED_CONSTRUCTORS = new HashMap<>();
    private static final Map<Class, Map<String, Constructor>> ALL_CONSTRUCTORS_PER_CLASS = new HashMap<>();
    private final Map<Class<?>, Map<Class<?>, Annotation>> ANNOTATIONS = new HashMap<>();
    private final Map<Class<?>, Map<Method, Map<Class<?>, Annotation>>> METHOD_ANNOTATIONS = new HashMap<>();
    private final Map<Class<?>, Map<Method, Annotation[][]>> PARAMETERS_ANNOTATIONS = new HashMap<>();
    private final Map<Class<?>, Method[]> METHODS = new HashMap<>();
    private final Map<String, byte[]> RESOURCES = new HashMap<>();

    public void addClassToCache(String className) throws Exception {
        if (!CACHE.containsKey(className)) {
            LOGGER.debug("Adding to cache: " + className + " in module " + getModule().getName());
            Class<?> clazz = getModule().getClassLoader().loadClass(className, true);
            CACHE.put(className, clazz);

            try {
                for (Constructor c : clazz.getConstructors()) {
                    if (!Modifier.isPublic(c.getModifiers())) {
                        continue;
                    }
                    StringBuilder key = new StringBuilder();
                    key.append(className);

                    for (Class p : c.getParameterTypes()) {
                        key.append("_" + p.getName());
                    }
                    CONSTRUCTORS.put(key.toString(), c);
                }
                ALL_CONSTRUCTORS_PER_CLASS.put(clazz, CONSTRUCTORS);
                ALL_CONSTRUCTORS.put(clazz, clazz.getConstructors());
                ALL_DECLARED_CONSTRUCTORS.put(clazz, clazz.getDeclaredConstructors());

            } catch (Exception ex) {
                // OK
            }
            Map<Class<?>, Annotation> map = new HashMap<>();
            for (Annotation a : clazz.getAnnotations()) {
                Class<? extends Annotation> type = a.annotationType();
                //System.out.println("Adding annotation " + type + " for annotation " + a);
                map.put(type, a);
            }
            if (!map.isEmpty()) {
                ANNOTATIONS.put(clazz, map);
            }
            if (clazz.getDeclaredMethods().length != 0) {
                METHODS.put(clazz, clazz.getDeclaredMethods());
                for (final Method method : clazz.getDeclaredMethods()) {
                    Map<Class<?>, Annotation> ma = new HashMap<>();
                    for (Annotation a : method.getDeclaredAnnotations()) {
                        //System.out.println("Adding Method annotation " + a + " on " + method.getName());
                        ma.put(a.annotationType(), a);
                    }
                    if (!ma.isEmpty()) {
                        Map<Method, Map<Class<?>, Annotation>> m = METHOD_ANNOTATIONS.get(clazz);
                        if (m == null) {
                            m = new HashMap<>();
                            METHOD_ANNOTATIONS.put(clazz, m);
                        }
                        m.put(method, ma);
                    }
                    Annotation[][] arr = method.getParameterAnnotations();
                    if (arr.length == 0) {
                        arr = new Annotation[0][0];
                    }
                    Map<Method, Annotation[][]> mp = PARAMETERS_ANNOTATIONS.get(clazz);
                    if (mp == null) {
                        mp = new HashMap<>();
                        PARAMETERS_ANNOTATIONS.put(clazz, mp);
                    }
                    mp.put(method, arr);
                }
            }
        }
    }

    public Constructor getConstructorFromCache(Class<?> clazz, Class<?>... parameterTypes) {
        LOGGER.debug("GET CONSTRUCTOR " + clazz + " FROM CACHE FROM MODULE " + getModule().getName());
        Map<String, Constructor> constructors = ALL_CONSTRUCTORS_PER_CLASS.get(clazz);
        if (constructors == null) {
            return null;
        }
        StringBuilder key = new StringBuilder();
        key.append(clazz.getName());
        if (parameterTypes != null) {
            for (Class<?> type : parameterTypes) {
                if (type != null) {
                    key.append("_" + type.getName());
                }
            }
        }
        Constructor ctr = constructors.get(key.toString());
        if (ctr != null) {
            LOGGER.debug("SUCCESS, FOUND CONSTRUCTOR IN " + getModule().getName());
        }
        return ctr;
    }

    private static Set<String> getClassesTree(Class clazz) {
        if (clazz.getSuperclass() == null) {
            return Collections.emptySet();
        }
        Set<String> classes = new HashSet<>();
        classes.add(clazz.getName());
        for (Class i : clazz.getInterfaces()) {
            classes.add(i.getName());
            classes.addAll(getClassesTree(i));
        }
        classes.addAll(getClassesTree(clazz.getSuperclass()));
        return classes;
    }

    public Set<String> addServiceToCache(String className) throws Exception {
        LOGGER.debug("ADD SERVICES " + className + " IN CACHE FOR MODULE " + getModule().getName());
        Set<String> ret = new HashSet<>();
        try {
            Class<?> clazz = getModule().getClassLoader().loadClass(className, true);
            if (!SERVICES.containsKey(clazz)) {
                ClassLoader orig = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(getModule().getClassLoader());
                try {
                    ServiceLoader<?> sl = ServiceLoader.load(clazz, getModule().getClassLoader());
                    List<Object> services = new ArrayList<>();
                    for (Object service : sl) {
                        if (service.getClass().getClassLoader() instanceof ModuleClassLoader) {
                            services.add(service);
                            ret.addAll(getClassesTree(service.getClass()));
                        }
                    }
                    if (!services.isEmpty()) {
                        SERVICES.put(clazz, services);
                    }
                } finally {
                    Thread.currentThread().setContextClassLoader(orig);
                }
            }
        } catch (Throwable ex) {
            //System.out.println("ERROR ADDING " + className + " service: " + ex);
        }
        return ret;
    }

    public List<Object> getServicesFromCache(Class<?> type) {
        List<Object> services = SERVICES.get(type);
        if (services != null && !services.isEmpty()) {
            LOGGER.debug("SUCCESS, found services " + type + " impl " + services + " from module " + getModule().getName());
        }
        return services;
    }

    public Class<?> getClassFromCache(String className) {
        Class<?> clazz = CACHE.get(className);
        LOGGER.debug("GET CLASS FROM CACHE " + className + " in cache " + clazz);
        return clazz;
    }

    public Annotation getAnnotation(Class<?> clazz, Class<? extends Annotation> type) {
        Map<Class<?>, Annotation> map = ANNOTATIONS.get(clazz);
        LOGGER.debug("GET ANNOTATION FROM CACHE " + clazz + " for annotation " + type + " in cache " + map + " from module " + getModule().getName());
        if (map == null) {
            return null;
        }
        return ANNOTATIONS.get(clazz).get(type);
    }

    public Annotation getAnnotation(Class<?> clazz, Method m, Class<? extends Annotation> type) {
        Map<Method, Map<Class<?>, Annotation>> map = METHOD_ANNOTATIONS.get(clazz);
        LOGGER.debug("GET ANNOTATION FROM CACHE " + clazz + " for annotation " + type + " in cache " + map + " from module " + getModule().getName());
        if (map == null) {
            return null;
        }
        Map<Class<?>, Annotation> map2 = map.get(m);
        //System.out.println("MAP FOR " + m + " " + map2);
        if (map2 == null) {
            return null;
        }
        return map2.get(type);
    }

    public Annotation[][] getParameterAnnotations(Class<?> clazz, Method m) {
        Map<Method, Annotation[][]> map = PARAMETERS_ANNOTATIONS.get(clazz);
        LOGGER.debug("GET ParameterAnnotations FROM CACHE " + clazz + " for annotation " + m + " in cache " + map + " from module " + getModule().getName());
        if (map == null) {
            return null;
        }
        Annotation[][] arr = map.get(m);
        return arr;
    }

    public Method[] getDeclaredMethods(Class<?> clazz) {
        Method[] methods = METHODS.get(clazz);
        LOGGER.debug("GET getDeclaredMethods FROM CACHE " + clazz + " in cache " + methods + " from module " + getModule().getName());
        if (methods == null) {
            methods = new Method[0];
        }
        return methods;
    }

    @Override
    public Method getMethod(Class<?> clazz, String name, Class<?>[] params) throws NoSuchMethodException {
        Method[] methods = METHODS.get(clazz);
        LOGGER.debug("GET getMethod FROM CACHE " + clazz + ", name " + name + " in cache " + methods + " from module " + getModule().getName());
        if (methods != null) {
            for (Method m : methods) {
                if (m.isBridge()) {
                    continue;
                }
                if (m.getName().equals(name)) {
                    if (params.length == m.getParameterCount()) {
                        boolean eq = true;
                        for (int i = 0; i < params.length; i++) {
                            if (!params[i].equals(m.getParameterTypes()[i])) {
                                eq = false;
                                break;
                            }
                        }
                        if (eq) {
                            return m;
                        }
                    }
                }
            }
        }
        throw new NoSuchMethodException("No such method " + name);
    }

    @Override
    public Constructor[] getDeclaredConstructors(Class<?> type) {
        Constructor[] constr = ALL_DECLARED_CONSTRUCTORS.get(type);
        LOGGER.debug("GET CONSTRUCTORS for  " + type + " in cache " + constr + " for module " + getModule().getName());
        return constr;
    }

    @Override
    public Constructor[] getConstructors(Class<?> type) {
        Constructor[] constr = ALL_CONSTRUCTORS.get(type);
        LOGGER.debug("GET CONSTRUCTORS for  " + type + " in cache " + constr + " for module " + getModule().getName());
        return constr;
    }

    @Override
    public void addResourceToCache(String path) throws IOException {
        try (InputStream stream = getModule().getClassLoader().getResourceAsStream(path)) {
            LOGGER.debug("ADD RESOURCE TO CACHE " + path + " for module " + getModule().getName());
            if (stream != null) {
                byte[] bytes = stream.readAllBytes();
                RESOURCES.put(path, bytes);
            }
        }
    }

    @Override
    public InputStream getResourceAsStream(String path) throws IOException {
        InputStream stream = null;
        byte[] arr = RESOURCES.get(path);
        LOGGER.debug("GET RESOURCE FROM CACHE " + path + ", in cache" + arr + ", for module " + getModule().getName());
        if (arr != null) {
            stream = new ByteArrayInputStream(arr);
        }
        return stream;
    }
}
