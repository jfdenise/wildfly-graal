package hello;

import java.util.ServiceLoader;

import api.Provider;

public class Hello {
    static Provider prov;
    public static void main(String[] args) throws Exception {
        final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class);
        prov = providerServiceLoader.iterator().next();
        System.out.println("The main pre-run has beel called " + Hello.class.getClassLoader());
    }
    
    public static void postMain() {
        System.out.println("HELLO " + prov.getIt());
    }

}
