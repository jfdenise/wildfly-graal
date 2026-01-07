package launcher;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.jboss.modules.ClassCache;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleDependencySpec;
import org.jboss.modules.ModuleLoadException;

/**
 *
 * @author jdenise
 */
public class Cache extends ClassCache {

    private final static Set<String> RECORDED_MODULES = new HashSet<>();
    private final static Set<String> RECORDED_CLASSES = new HashSet<>();

    static {
        String mods = System.getProperty("org.jboss.modules.record.classes.of");
        if (mods != null) {
            String[] split = mods.split(",");
            for (String s : split) {
                s = s.trim();
                if (!s.isEmpty()) {
                    RECORDED_MODULES.add(s);
                }
            }
        }
        String classes = System.getProperty("org.jboss.modules.record.classes");
        if (classes != null) {
            String[] split = classes.split(",");
            for (String s : split) {
                s = s.trim();
                if (!s.isEmpty()) {
                    RECORDED_CLASSES.add(s);
                }
            }
        }
    }

    private final Map<String, Class<?>> CACHE = new HashMap<>();
    private final Map<Class<?>, List<Object>> SERVICES = new HashMap<>();
    private final Map<String, Constructor> CONSTRUCTORS = new HashMap<>();
    private final Map<Class<?>, Map<Class<?>, Annotation>> ANNOTATIONS = new HashMap<>();
    private final Map<Class<?>, Map<Method, Map<Class<?>, Annotation>>> METHOD_ANNOTATIONS = new HashMap<>();
    private final Map<Class<?>, Map<Method, Annotation[][]>> PARAMETERS_ANNOTATIONS = new HashMap<>();
    private final Map<Class<?>, Method[]> METHODS = new HashMap<>();

    public void addClassToCache(String className) throws Exception {
        addClassToCache(className, null);
    }

    public void addClassToCache(String className, Class<?>... parameterTypes) throws Exception {
        if (!CACHE.containsKey(className)) {
            System.out.println("Adding to cache: " + className + " in module " + getModule().getName());
            Class<?> clazz = getModule().getClassLoader().loadClass(className, true);
            CACHE.put(className, clazz);
            try {
                Constructor ctr = null;
                if (parameterTypes == null || parameterTypes.length == 0) {
                    ctr = clazz.getConstructor();
                } else {
                    ctr = clazz.getConstructor(parameterTypes);
                }
                CONSTRUCTORS.put(className, ctr);
            } catch (Exception ex) {
                // OK
            }
        }
    }

    public Constructor getConstructorFromCache(String className, Class<?>... parameterTypes) {
        StringBuilder key = new StringBuilder();
        key.append(className);
        if (parameterTypes != null) {
            for (Class<?> type : parameterTypes) {
                key.append("_" + type.getName());
            }
        }
        Constructor ctr = CONSTRUCTORS.get(key.toString());
        if (ctr == null) {
            for (DependencySpec spec : getModule().getDependencies()) {
                if (spec instanceof ModuleDependencySpec) {
                    ModuleDependencySpec md = (ModuleDependencySpec) spec;
                    try {
                        // Can be null for java.base, ...
                        if (md.getModuleLoader() != null) {
                            Module m = md.getModuleLoader().loadModule(md.getName());
                            ctr = m.getCache().getConstructorFromCache(className, parameterTypes);
                            if (ctr != null) {
                                break;
                            }
                        }
                    } catch (ModuleLoadException ex) {
                        // Ok, not found
                    }
                }
            }
        } else {
            System.out.println("SUCCESS, FOUND CONSTRUCTOR IN " + getModule().getName());
        }
        return ctr;
    }

    public void addServiceToCache(String className) throws Exception {
        Class<?> clazz = getModule().getClassLoader().loadClass(className, true);
        if (!SERVICES.containsKey(clazz)) {
            ClassLoader orig = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(getModule().getClassLoader());
            try {
                ServiceLoader<?> sl = ServiceLoader.load(clazz, getModule().getClassLoader());
                List<Object> services = new ArrayList<>();
                for (Object service : sl) {
                    if (service.getClass().getClassLoader() instanceof ModuleClassLoader) {
                        //System.out.println("CACHE SERVICE " + service + " of type " + clazz);
                        services.add(service);
                    }
                }
                if (!services.isEmpty()) {
                    SERVICES.put(clazz, services);
                } else {
                    //System.out.print("!!!!!!!!!!!! NO SERVICE TO CACHE FOR " + className);
                }
            } finally {
                Thread.currentThread().setContextClassLoader(orig);
            }
        }
    }

    public List<Object> getServicesFromCache(Class<?> type) {
        return SERVICES.get(type);
    }

    public Class<?> getClassFromCache(String className) {
        return CACHE.get(className);
    }

    public Annotation getAnnotation(Class<?> clazz, Class<? extends Annotation> type) {
        Map<Class<?>, Annotation> map = ANNOTATIONS.get(clazz);
        //System.out.println("MAP FOR " + clazz + " " + map);
        if (map == null) {
            return null;
        }
        return ANNOTATIONS.get(clazz).get(type);
    }

    public Annotation getAnnotation(Class<?> clazz, Method m, Class<? extends Annotation> type) {
        Map<Method, Map<Class<?>, Annotation>> map = METHOD_ANNOTATIONS.get(clazz);
        //System.out.println("MAP FOR " + clazz + " " + map);
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
        if (map == null) {
            return null;
        }
        Annotation[][] arr = map.get(m);
        return arr;
    }

    public Method[] getDeclaredMethods(Class<?> clazz) {
        Method[] methods = METHODS.get(clazz);
        if (methods == null) {
            methods = new Method[0];
        }
        return methods;
    }

    public void recordClass(Class clazz) {
        synchronized (this) {
            if (!RECORDED_MODULES.isEmpty()) {
                String className = clazz.getName();
                if (className.startsWith("java.") || CACHE.containsKey(className)) {
                    return;
                }
                if (RECORDED_MODULES.contains(getModule().getName()) && RECORDED_CLASSES.contains(clazz.getPackageName())) {

                    System.out.println(getModule().getName() + " module, recording class: " + className);
                    CACHE.put(className, clazz);
                    // Add default constructor if it exists
                    try {
                        CONSTRUCTORS.put(className, clazz.getConstructor());
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
                    //System.out.println("END RECORDING " + clazz);
                }
            }
        }
    }

    @Override
    public Method getMethod(Class<?> clazz, String name, Class<?>[] params) throws NoSuchMethodException {
        Method[] methods = METHODS.get(clazz);
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
}
