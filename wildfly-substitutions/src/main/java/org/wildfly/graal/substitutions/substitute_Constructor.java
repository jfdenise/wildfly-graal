package org.wildfly.graal.substitutions;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import java.lang.reflect.InvocationTargetException;
import org.jboss.modules.ModuleClassLoader;

//@TargetClass(className = "org.jboss.modules.ModuleClassLoader")
public final class substitute_Constructor {

    @Alias
    private Class<?> clazz;

    @Substitute()
    public Object newInstance(Object... initargs)
            throws InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        ClassLoader loader = clazz.getClassLoader();
        if (loader instanceof ModuleClassLoader) {
            
        } 
        return null;
    }
}
