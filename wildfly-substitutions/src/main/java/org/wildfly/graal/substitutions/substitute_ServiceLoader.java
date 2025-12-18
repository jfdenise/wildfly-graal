package org.wildfly.graal.substitutions;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jboss.modules.ModuleClassLoader;

@TargetClass(className = "java.util.ServiceLoader")
public final class substitute_ServiceLoader {

    @Alias
    private Class<?> service;
    @Alias
    private ClassLoader loader;

    @Alias
    private Iterator<ServiceLoader.Provider> newLookupIterator() {
        return null;
    }
    @Alias
    private int reloadCount;
    @Alias
    private List<Object> instantiatedProviders;
    @Alias
    private Iterator<ServiceLoader.Provider> lookupIterator1;
    @Alias
    private boolean loadedAllProviders;
    @Alias
    private List<ServiceLoader.Provider> loadedProviders;
    @Alias
    private Iterator<ServiceLoader.Provider> lookupIterator2;

    @Substitute()
    Iterator<?> iterator() {
        if (loader instanceof ModuleClassLoader) {
            ModuleClassLoader cl = (ModuleClassLoader) loader;
            
            List<Object> lst = cl.getModule().getCache().getServicesFromCache(service);
            if(lst != null && !lst.isEmpty()) {
               // System.out.println("@@@@ Service " + service.getName() + " found in " + cl.getModule().getName());
               // System.out.println(lst);
               // System.out.println("@@@@");
            } else {
                //System.out.println("@@@@ NO Service " + service.getName() + "found in " + cl.getModule().getName());
            }
            return lst == null ? new ArrayList<>().iterator() : lst.iterator();
        } else {
            if (lookupIterator1 == null) {
                lookupIterator1 = newLookupIterator();
            }
            return new Iterator() {

                // record reload count
                final int expectedReloadCount = reloadCount;

                // index into the cached providers list
                int index;

                /**
                 * Throws ConcurrentModificationException if the list of cached
                 * providers has been cleared by reload.
                 */
                private void checkReloadCount() {
                    if (reloadCount != expectedReloadCount) {
                        throw new ConcurrentModificationException();
                    }
                }

                @Override
                public boolean hasNext() {
                    checkReloadCount();
                    if (index < instantiatedProviders.size()) {
                        return true;
                    }
                    return lookupIterator1.hasNext();
                }

                @Override
                public Object next() {
                    checkReloadCount();
                    Object next;
                    if (index < instantiatedProviders.size()) {
                        next = instantiatedProviders.get(index);
                    } else {
                        next = lookupIterator1.next().get();
                        instantiatedProviders.add(next);
                    }
                    index++;
                    return next;
                }

            };
        }
    }

    private final class ProviderSpliterator implements Spliterator<ServiceLoader.Provider> {

        final int expectedReloadCount = reloadCount;
        final Iterator<ServiceLoader.Provider> iterator;
        int index;

        ProviderSpliterator(Iterator<ServiceLoader.Provider> iterator) {
            this.iterator = iterator;
        }

        @Override
        public Spliterator<ServiceLoader.Provider> trySplit() {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean tryAdvance(Consumer<? super ServiceLoader.Provider> action) {
            if (reloadCount != expectedReloadCount) {
                throw new ConcurrentModificationException();
            }
            ServiceLoader.Provider next = null;
            if (index < loadedProviders.size()) {
                next = (ServiceLoader.Provider) loadedProviders.get(index++);
            } else if (iterator.hasNext()) {
                next = iterator.next();
                loadedProviders.add((ServiceLoader.Provider<?>) next);
                index++;
            } else {
                loadedAllProviders = true;
            }
            if (next != null) {
                action.accept(next);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public int characteristics() {
            // not IMMUTABLE as structural interference possible
            // not NOTNULL so that the characteristics are a subset of the
            // characteristics when all Providers have been located.
            return Spliterator.ORDERED;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }
    }

    private static final class MyProvider implements ServiceLoader.Provider {

        private final Class clazz;
        private final Object obj;

        MyProvider(Class clazz, Object obj) {
            this.clazz = clazz;
            this.obj = obj;
        }

        @Override
        public Class type() {
            return clazz;
        }

        @Override
        public Object get() {
            return obj;
        }
    }

    @Substitute
    public Stream<ServiceLoader.Provider> stream() {
        if (loader instanceof ModuleClassLoader) {
            ModuleClassLoader cl = (ModuleClassLoader) loader;
           // System.out.println("SUBSTITUTE " + cl.getModule().getName());
            List<Object> lst = cl.getModule().getCache().getServicesFromCache(service);
            if (lst != null && !lst.isEmpty()) {
               // System.out.println("@@@@ Service " + service.getName() + " found in " + cl.getModule().getName());
               // System.out.println(lst);
               // System.out.println("@@@@");
            } else {
                //System.out.println("@@@@ NO Service " + service.getName() + "found in " + cl.getModule().getName());
            }
            List<ServiceLoader.Provider> providers = new ArrayList<>();
            if(lst != null) {
                for (Object obj : lst) {
                    providers.add(new MyProvider(obj.getClass(), obj));
                }
            }
            return providers.stream();
        } else {
            // use cached providers as the source when all providers loaded
            if (loadedAllProviders) {
                return loadedProviders.stream();
            }

            // create lookup iterator if needed
            if (lookupIterator2 == null) {
                lookupIterator2 = newLookupIterator();
            }

            // use lookup iterator and cached providers as source
            Spliterator<ServiceLoader.Provider> s = new ProviderSpliterator(lookupIterator2);
            return StreamSupport.stream(s, false);
        }
    }
}
