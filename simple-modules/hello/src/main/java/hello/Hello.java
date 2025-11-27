package hello;

import java.util.ServiceLoader;

import api.Provider;
import java.lang.reflect.Method;
import org.jboss.modules.ModuleClassLoader;

public class Hello {

    static Provider prov;
    static String msg;
    static Class<?> clazz1;
    static Class<?> clazz2;
    static Method m1;
    static Method m2;
    public static void main(String[] args) throws Exception {
        final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class);
        prov = providerServiceLoader.iterator().next();
        msg = "INIT IN MAIN";
        System.out.println("The main pre-run has beel called " + Hello.class.getClassLoader() + " provider is " + prov.getIt());
        System.out.println("Access to mod1_v1");
        if (Hello.class.getClassLoader() instanceof ModuleClassLoader) {
            System.out.println("USING JBoss Modules ");
            ModuleClassLoader mcls = (ModuleClassLoader) Hello.class.getClassLoader();
            org.jboss.modules.Module v1 = mcls.getModule().getModuleLoader().loadModule("mod1_v1");
            clazz1 = v1.getClassLoader().loadClass("mod1.Version");
            //System.out.println("V1 VERSION " + clazz1.getMethod("getVersion").invoke(null));
            m1 = clazz1.getMethod("getVersion");
            org.jboss.modules.Module v2 = mcls.getModule().getModuleLoader().loadModule("mod1_v2");
            clazz2 = v2.getClassLoader().loadClass("mod1.Version");
            m2 = clazz2.getMethod("getVersion");
            //System.out.println("V2 VERSION " + clazz2.getMethod("getVersion").invoke(null));
        } else {
            System.out.println("USING JPMS");
            ClassLoader loader1 = Hello.class.getModule().getLayer().findModule("Mod1V1Module").get().getClassLoader();
            clazz1 = loader1.loadClass("mod1.Version");
            
            ClassLoader loader2 = Hello.class.getModule().getLayer().findModule("Mod1V2Module").get().getClassLoader();
            clazz2 = loader2.loadClass("mod1.Version");
        }
    }

    public static void postMain() throws Exception {
        System.out.println("MSG IS" + msg + " Class loader is" + Hello.class.getClassLoader());
        System.out.println("HELLO " + prov.getIt());
        System.out.println("V1 VERSION " + m1.invoke(null));
        System.out.println("V2 VERSION " + m2.invoke(null));
    }

}
