package hello;

import java.util.ServiceLoader;

import api.Provider;

public class Hello {
    static Provider prov;
    static String msg;
    public static void main(String[] args) throws Exception {
        final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class);
        prov = providerServiceLoader.iterator().next();
        msg = "INIT IN MAIN";
        System.out.println("The main pre-run has beel called " + Hello.class.getClassLoader() + " provider is " + prov.getIt());
    }
    
    public static void postMain() {
        System.out.println("MSG IS" + msg  + " Class loader is" + Hello.class.getClassLoader());
        System.out.println("HELLO " + prov.getIt());
    }

}
