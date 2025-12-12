package org.wildfly.graal.substitutions;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.lang.reflect.Constructor;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;

//@TargetClass(className = "java.lang.Class")
public final class substitute_Class {

//    @Alias
//    private String name;
    @Alias
    public String toGenericString() {
        return null;
    }

    @Substitute()
    public Constructor getDeclaredConstructor(Class<?>... parameterTypes)
            throws NoSuchMethodException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader instanceof ModuleClassLoader) {
            ModuleClassLoader cl = (ModuleClassLoader) loader;
            String genString = toGenericString();
            String[] split = genString.split(" ");
            String name = null;
            for (String s : split) {
                if (s.contains(".")) {
                    name = s;
                }
            }
            if (name != null) {
                Constructor c = cl.getModule().getConstructorFromCache(name, parameterTypes);
                if (c != null) {
                    return c;
                }
            }
        }
        throw new java.lang.NoSuchMethodException("No such constructor defined " + toGenericString());
    }
    
    @Substitute()
    public Constructor getConstructor(Class<?>... parameterTypes)
            throws NoSuchMethodException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader instanceof ModuleClassLoader) {
            ModuleClassLoader cl = (ModuleClassLoader) loader;
            String genString = toGenericString();
            String[] split = genString.split(" ");
            String name = null;
            for (String s : split) {
                if (s.contains(".")) {
                    name = s;
                }
            }
            if (name != null) {
                Constructor c = cl.getModule().getConstructorFromCache(name, parameterTypes);
                if (c != null) {
                    return c;
                }
            }
        }
        throw new java.lang.NoSuchMethodException("No such constructor defined " + toGenericString());
    }
}
