package hello;

import java.util.ServiceLoader;

import api.Provider;

public class Hello {

    public static void main(String[] args) throws Exception {
        final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class);
        Provider p = providerServiceLoader.iterator().next();
        System.out.println("HELLO " + p.getIt());
    }

}
