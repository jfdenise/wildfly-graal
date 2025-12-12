package org.wildfly.graal.substitutions;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;

@TargetClass(className = "org.jboss.modules.ModuleClassLoader")
public final class substitute_ModuleClassLoader {
    @Alias
    private org.jboss.modules.Module module;
    @Alias
    private Class<?> getLoadedClass(String className, boolean resolve) {
        return null;
    }
    @Alias
    protected String getClassNotFoundExceptionMessage(String className, org.jboss.modules.Module fromModule){
        return null;
    }
    @Substitute()
    private Class<?> doDefineOrLoadClass(final String className, final byte[] bytes, final ByteBuffer byteBuffer, ProtectionDomain protectionDomain) {
        return module.getFromCache(className);
    }
    @Substitute()
    protected final Class<?> findClass(String className, boolean exportsOnly, final boolean resolve) throws ClassNotFoundException {
        className = className.replace('/', '.');
        Class<?> clazz = getLoadedClass(className, resolve);
        if(clazz == null) {
            clazz = module.getFromCache(className);
            if(clazz == null) {
                module.dumpCache();
                throw new ClassNotFoundException(getClassNotFoundExceptionMessage(className, module));
            }
        }
        return clazz;
    }
}
