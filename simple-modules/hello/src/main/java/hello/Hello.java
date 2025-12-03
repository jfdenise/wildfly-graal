package hello;


import api.Provider;
import hello.logging.ServerLogger;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;

public class Hello {
    private static final MBeanServer MBEAN_SERVER;

    static {
                System.out.println("INIT HELLO " + Hello.class.getClassLoader());

        MBeanServer mBeanServer = null;
        try {
            mBeanServer = ManagementFactory.getPlatformMBeanServer();
        } catch (final Exception e) {
            //ServerLogger.WARNING_LOGGER.warning(e.toString());
        } finally {
            MBEAN_SERVER = mBeanServer;
        }
    }
    static {
        
    }
    static Provider prov;
    //static mod1.Version vers;
//    static ProviderImpl impl;
//    static String msg;
//    static Class<?> clazz1;
//    static Class<?> clazz2;
//    static Method m1;
//    static Method m2;
    public static void main(String[] args) throws Exception {
                ServerLogger.WARNING_LOGGER.warning("BERS");

        //System.out.println("VERSION CLASS : " + Hello.class.getClassLoader().loadClass("mod1.Version").getClassLoader());
        System.out.println("RUN MAIN CTX " + Thread.currentThread().getContextClassLoader());
        System.out.println("MAIN " + Hello.class.getClassLoader());
        mod1.Version v = new mod1.Version();
        System.out.println(v.getVersion());
//        System.out.println("PROVIDER API CLASS " + Provider.class.getClassLoader());
//        
//        System.out.println("PROVIDER " + ResourceHolder.provider.getClass().getClassLoader());
//        ModuleClassLoader mcls = (ModuleClassLoader) Hello.class.getClassLoader();
//        org.jboss.modules.Module v1 = mcls.getModule().getModuleLoader().loadModule("mod1_v1");
//        Class clazz1 = v1.getClassLoader().loadClass("mod1.Version");
//        System.out.println("V1 VERSION " + clazz1.getMethod("getVersion").invoke(null));
//            org.jboss.modules.Module v1 = mcls.getModule().getModuleLoader().loadModule("mod1_v1");
//        final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class);
//        prov = providerServiceLoader.iterator().next();
//        msg = "INIT IN MAIN";
//        System.out.println("The main pre-run has beel called " + Hello.class.getClassLoader() + " provider is " + prov.getIt());
//        System.out.println("Access to mod1_v1");
//        if (Hello.class.getClassLoader() instanceof ModuleClassLoader) {
//            System.out.println("USING JBoss Modules ");
//            ModuleClassLoader mcls = (ModuleClassLoader) Hello.class.getClassLoader();
//            org.jboss.modules.Module v1 = mcls.getModule().getModuleLoader().loadModule("mod1_v1");
//            clazz1 = v1.getClassLoader().loadClass("mod1.Version");
//            //System.out.println("V1 VERSION " + clazz1.getMethod("getVersion").invoke(null));
//            m1 = clazz1.getMethod("getVersion");
//            org.jboss.modules.Module v2 = mcls.getModule().getModuleLoader().loadModule("mod1_v2");
//            clazz2 = v2.getClassLoader().loadClass("mod1.Version");
//            m2 = clazz2.getMethod("getVersion");
//            //System.out.println("V2 VERSION " + clazz2.getMethod("getVersion").invoke(null));
//        } else {
//            System.out.println("USING JPMS");
//            ClassLoader loader1 = Hello.class.getModule().getLayer().findModule("Mod1V1Module").get().getClassLoader();
//            clazz1 = loader1.loadClass("mod1.Version");
//            
//            ClassLoader loader2 = Hello.class.getModule().getLayer().findModule("Mod1V2Module").get().getClassLoader();
//            clazz2 = loader2.loadClass("mod1.Version");
//        }
    }

    public static void preMain() throws Exception {
        //mod1.Version v = new mod1.Version();
        //System.out.println(v.getVersion());
        //    System.out.println("PRE MAIN CTX " + Thread.currentThread().getContextClassLoader());
        //    System.out.println("PRE MAIN " + Hello.class.getClassLoader());
        //    System.out.println("PRE MAIN PROVIDER API CLASS " + Provider.class.getClassLoader());
            //final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class, Hello.class.getClassLoader());
        //prov = providerServiceLoader.iterator().next();
        //final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class, Hello.class.getClassLoader());
        //Provider prov = providerServiceLoader.iterator().next();
        //System.out.println("PRE MAIN PROVIDER " + prov.getClass().getClassLoader());
//        System.out.println("MSG IS" + msg + " Class loader is" + Hello.class.getClassLoader());
//        System.out.println("HELLO " + prov.getIt());
//        System.out.println("V1 VERSION " + m1.invoke(null));
//        System.out.println("V2 VERSION " + m2.invoke(null));
    }

}
