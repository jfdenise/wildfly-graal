package launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoader;

public class Launcher {

    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";

    // We keep a map of all modules to be able to restore permissions
    private static final Map<String, Module> modules = new HashMap<>();
    private static Module mainModule;
    private static final String JBOSS_HOME = System.getProperty("jboss.home.dir");

    static {
        try {
            // No more needed, although how the classes are seen is not understood.
            List<String> depClasses = Files.readAllLines(Paths.get("allDeploymentClasses.txt"));
            StringBuilder classesBuilder = new StringBuilder();
            for (String s : depClasses) {
                if (!classesBuilder.isEmpty()) {
                    classesBuilder.append(",");
                }
                classesBuilder.append(s);

            }
            // JAX-RS
            // This should be made conditional to the presence of modules.
            String resteasyProviders = 
                    //"org.jboss.resteasy.plugins.providers.jaxb.fastinfoset.FastinfoSetJAXBContextFinder,"
                   // + "org.jboss.resteasy.plugins.providers.jaxb.fastinfoset.FastinfoSetElementProvider,"
                    //+ "org.jboss.resteasy.plugins.providers.jaxb.fastinfoset.FastinfoSetXmlRootElementProvider,"
                    //+ "org.jboss.resteasy.plugins.providers.jaxb.fastinfoset.FastinfoSetXmlSeeAlsoProvider,"
                    //+ "org.jboss.resteasy.plugins.providers.jaxb.fastinfoset.FastinfoSetXmlTypeProvider,"
                    "org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider,"
                    + "org.jboss.resteasy.plugins.providers.jackson.PatchMethodFilter,"
                    + "org.jboss.resteasy.plugins.providers.jackson.JsonProcessingExceptionMapper,"
                    + "org.jboss.resteasy.plugins.providers.jaxb.JAXBXmlSeeAlsoProvider,"
                    + "org.jboss.resteasy.plugins.providers.jaxb.JAXBXmlRootElementProvider,"
                    + "org.jboss.resteasy.plugins.providers.jaxb.JAXBElementProvider,"
                    + "org.jboss.resteasy.plugins.providers.jaxb.JAXBXmlTypeProvider,"
                    + "org.jboss.resteasy.plugins.providers.jaxb.CollectionProvider,"
                    + "org.jboss.resteasy.plugins.providers.jaxb.MapProvider,"
                    + "org.jboss.resteasy.plugins.providers.jaxb.XmlJAXBContextFinder,"
                    + "org.jboss.resteasy.plugins.providers.jsonb.JsonBindingProvider,"
                    + "org.jboss.resteasy.plugins.providers.jsonp.JsonArrayProvider,"
                    + "org.jboss.resteasy.plugins.providers.jsonp.JsonStructureProvider,"
                    + "org.jboss.resteasy.plugins.providers.jsonp.JsonObjectProvider,"
                    + "org.jboss.resteasy.plugins.providers.jsonp.JsonValueProvider,"
                    + "org.jboss.resteasy.plugins.providers.jsonp.JsonpPatchMethodFilter,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MultipartEntityPartWriter,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MultipartEntityPartReader,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MultipartReader,"
                    + "org.jboss.resteasy.plugins.providers.multipart.ListMultipartReader,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataReader,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedReader,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MapMultipartFormDataReader,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MultipartWriter,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataWriter,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedWriter,"
                    + "org.jboss.resteasy.plugins.providers.multipart.ListMultipartWriter,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MapMultipartFormDataWriter,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MultipartFormAnnotationReader,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MultipartFormAnnotationWriter,"
                    + "org.jboss.resteasy.plugins.providers.multipart.MimeMultipartProvider,"
                    + "org.jboss.resteasy.plugins.providers.multipart.XopWithMultipartRelatedReader,"
                    + "org.jboss.resteasy.plugins.providers.multipart.XopWithMultipartRelatedWriter,"
                    + "org.jboss.resteasy.plugins.providers.multipart.EntityPartFilter,"
                    + "org.jboss.resteasy.plugins.providers.atom.AtomFeedProvider,"
                    + "org.jboss.resteasy.plugins.providers.atom.AtomEntryProvider,"
                    //+ "org.jboss.resteasy.plugins.providers.html.HtmlRenderableWriter,"
                   // + "org.jboss.resteasy.plugins.validation.ValidatorContextResolver,"
                    //+ "org.jboss.resteasy.plugins.validation.ValidatorContextResolverCDI,"
                    //+ "org.jboss.resteasy.plugins.validation.ResteasyViolationExceptionMapper,"
                    + "org.jboss.resteasy.client.jaxrs.internal.CompletionStageRxInvokerProvider,"
                    + "org.jboss.resteasy.plugins.providers.AsyncStreamingOutputProvider,"
                    + "org.jboss.resteasy.plugins.providers.DataSourceProvider,"
                    + "org.jboss.resteasy.plugins.providers.DocumentProvider,"
                    + "org.jboss.resteasy.plugins.providers.DefaultTextPlain,"
                    + "org.jboss.resteasy.plugins.providers.DefaultNumberWriter,"
                    + "org.jboss.resteasy.plugins.providers.DefaultBooleanWriter,"
                    + "org.jboss.resteasy.plugins.providers.StringTextStar,"
                    + "org.jboss.resteasy.plugins.providers.SourceProvider,"
                    + "org.jboss.resteasy.plugins.providers.InputStreamProvider,"
                    + "org.jboss.resteasy.plugins.providers.ReaderProvider,"
                    + "org.jboss.resteasy.plugins.providers.ByteArrayProvider,"
                    + "org.jboss.resteasy.plugins.providers.FormUrlEncodedProvider,"
                    + "org.jboss.resteasy.plugins.providers.JaxrsFormProvider,"
                    + "org.jboss.resteasy.plugins.providers.CompletionStageProvider,"
                    + "org.jboss.resteasy.plugins.providers.ReactiveStreamProvider,"
                    + "org.jboss.resteasy.plugins.providers.FileProvider,"
                    + "org.jboss.resteasy.plugins.providers.FileRangeWriter,"
                    + "org.jboss.resteasy.plugins.providers.StreamingOutputProvider,"
                    + "org.jboss.resteasy.plugins.providers.IIOImageProvider,"
                    + "org.jboss.resteasy.plugins.providers.MultiValuedParamConverterProvider,"
                    + "org.jboss.resteasy.plugins.interceptors.CacheControlFeature,"
                    + "org.jboss.resteasy.plugins.interceptors.ClientContentEncodingAnnotationFeature,"
                    + "org.jboss.resteasy.plugins.interceptors.ServerContentEncodingAnnotationFeature,"
                    + "org.jboss.resteasy.plugins.interceptors.MessageSanitizerContainerResponseFilter,"
                    + "org.jboss.resteasy.plugins.providers.sse.SseEventProvider,"
                    + "org.jboss.resteasy.plugins.providers.sse.SseEventSinkInterceptor,"
                    //+ "org.jboss.resteasy.reactor.MonoProvider,"
                    //+ "org.jboss.resteasy.reactor.MonoRxInvokerImpl,"
                    //+ "org.jboss.resteasy.reactor.MonoRxInvokerProvider,"
                    //+ "org.jboss.resteasy.reactor.FluxProvider,"
                    //+ "org.jboss.resteasy.reactor.FluxRxInvokerImpl,"
                    //+ "org.jboss.resteasy.reactor.FluxRxInvokerProvider,"
//                    + "org.jboss.resteasy.rxjava2.SingleProvider,"
//                    + "org.jboss.resteasy.rxjava2.SingleRxInvokerImpl,"
//                    + "org.jboss.resteasy.rxjava2.SingleRxInvokerProvider,"
//                    + "org.jboss.resteasy.rxjava2.ObservableProvider,"
//                    + "org.jboss.resteasy.rxjava2.ObservableRxInvokerImpl,"
//                    + "org.jboss.resteasy.rxjava2.ObservableRxInvokerProvider,"
//                    + "org.jboss.resteasy.rxjava2.FlowableProvider,"
//                    + "org.jboss.resteasy.rxjava2.FlowableRxInvokerImpl,"
//                    + "org.jboss.resteasy.rxjava2.FlowableRxInvokerProvider,"
//                    + "org.jboss.resteasy.rxjava2.propagation.RxJava2ContextPropagator,"
                    + "org.jboss.resteasy.security.doseta.ServerDigitalSigningHeaderDecoratorFeature,"
                    + "org.jboss.resteasy.security.doseta.ClientDigitalSigningHeaderDecoratorFeature,"
                    + "org.jboss.resteasy.security.doseta.ClientDigitalVerificationHeaderDecoratorFeature,"
                    + "org.jboss.resteasy.security.doseta.ServerDigitalVerificationHeaderDecoratorFeature,"
                    + "org.jboss.resteasy.security.doseta.DigitalSigningInterceptor,"
                    + "org.jboss.resteasy.security.doseta.DigitalVerificationInterceptor,"
                    + "org.jboss.resteasy.security.smime.EnvelopedReader,"
                    + "org.jboss.resteasy.security.smime.EnvelopedWriter,"
                    + "org.jboss.resteasy.security.smime.MultipartSignedReader,"
                    + "org.jboss.resteasy.security.smime.MultipartSignedWriter,"
                    + "org.jboss.resteasy.security.smime.PKCS7SignatureWriter,"
                    + "org.jboss.resteasy.security.smime.PKCS7SignatureTextWriter,"
                    + "org.jboss.resteasy.security.smime.PKCS7SignatureReader,"
                    + "org.jboss.resteasy.plugins.interceptors.AcceptEncodingGZIPFilter,"
                    + "org.jboss.resteasy.plugins.interceptors.GZIPDecodingInterceptor,"
                    + "org.jboss.resteasy.plugins.interceptors.GZIPEncodingInterceptor,"
                    + "org.jboss.resteasy.plugins.providers.jackson.Jackson2JsonpInterceptor";
                    //+ "org.jboss.resteasy.plugins.providers.SerializableProvider";
            classesBuilder.append(",jakarta.servlet.jsp.jstl.tlv.PermittedTaglibsTLV,"
                    + "jakarta.servlet.jsp.jstl.tlv.ScriptFreeTLV,"
                    + "com.fasterxml.jackson.databind.type.TypeFactory,"
                    + "org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher,"
                    // Required by QueryInjector resteasy
                    +"java.util.ArrayList,java.util.TreeSet,java.util.HashSet");
            //classesBuilder.append(resteasyProviders);
            // Will be consumed by the created Deployment Module
            // This appears to be no more required...All classes are seen,
            // root cause not understood!
            System.setProperty("org.wildfly.graal.deployment.classes", classesBuilder.toString());
            System.setProperty("org.wildfly.graal.build.time", "true");
            System.setProperty("org.wildfly.graal.cache.class", "launcher.Cache");
            Path modulesDir = Paths.get(JBOSS_HOME + "/modules").toAbsolutePath();
            LocalModuleLoader loader = (LocalModuleLoader) setupModuleLoader(modulesDir.toString());

            Map<String, Path> all = new HashMap<>();
            // Load all modules to have them accessible at runtime, and register as ParrallelCapable.
            handleModules(modulesDir, all);
            for (String k : all.keySet()) {
                //System.out.println("Load module " + k);
                try {
                    Module mod = loader.loadModule(k);
                    Cache classCache = new Cache();
                    mod.setClassCache(classCache);
                    if (k.equals("org.jboss.as.standalone")) {
                        mainModule = mod;
                    }
//                    if(k.equals("org.jboss.resteasy.resteasy-crypto")) {
//                        for (String serviceClass : mod.getServices()) {
//                            if(serviceClass.equals("jakarta.ws.rs.ext.Providers")) {
//                                System.out.println("FOUND IN CRYPTO");
//                                Class x = mod.getClassLoader().loadClass(serviceClass);
//                                System.out.println("CLAZZ CL " + ((ModuleClassLoader) x.getClassLoader()).getModule().getName());
//                                ServiceLoader l = ServiceLoader.load(x, mod.getClassLoader());
//                                for (Object service : l) {
//                                    System.out.println("SERVICE " + service.getClass());
//                                }
//                            }
//                        }
//                    }
                    for (String serviceClass : mod.getServices()) {
                        if (!serviceClass.startsWith("java.lang.")) {
                            mod.getCache().addServiceToCache(serviceClass);
                        }
                    }
                    modules.put(k, mod);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    System.out.println("EX " + ex);
                    throw ex;
                }
            }
            mainModule.preRun(new String[0]);
            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            System.out.println("The server classes that we add to the cache");
            modules.get("org.wildfly.extension.undertow").getCache().addClassToCache("org.apache.jasper.compiler.JspRuntimeContext");
            modules.get("org.wildfly.extension.undertow").getCache().addClassToCache("org.apache.jasper.servlet.JspServlet");
            modules.get("org.wildfly.extension.undertow").getCache().addClassToCache("org.wildfly.extension.undertow.deployment.JspInitializationListener");
            modules.get("org.wildfly.extension.undertow").getCache().addClassToCache("io.undertow.servlet.handlers.DefaultServlet");

            modules.get("io.undertow.websocket").getCache().addClassToCache("io.undertow.websockets.jsr.JsrWebSocketFilter");
            modules.get("io.undertow.websocket").getCache().addClassToCache("io.undertow.websockets.jsr.JsrWebSocketFilter$LogoutListener");
            modules.get("io.undertow.websocket").getCache().addClassToCache("io.undertow.websockets.jsr.Bootstrap$WebSocketListener");

            modules.get("io.undertow.core").getCache().addClassToCache("io.undertow.server.DirectByteBufferDeallocator");
            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");

            for (String k : modules.keySet()) {
                Module m = modules.get(k);
                m.cleanupPermissions();
            }
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) throws Exception {
        System.clearProperty("org.wildfly.graal.build.time");
        System.setProperty("org.wildfly.graal", "true");
        System.setProperty("jboss.home.dir", JBOSS_HOME);
        System.out.println("Running Main entry point");
        for (String k : modules.keySet()) {
            Module m = modules.get(k);
            m.restorePermissions();
        }
        mainModule.run(args);
    }

    static void handleModules(Path modulesDir, Map<String, Path> moduleXmlByPkgName) throws IOException {
        final Path layersDir = modulesDir.resolve("system").resolve("layers").resolve("base");
        try (Stream<Path> layers = Files.list(layersDir)) {
            final Iterator<Path> i = layers.iterator();
            while (i.hasNext()) {
                final Path layerDir = i.next();
                findModules(layerDir, moduleXmlByPkgName);
                if (moduleXmlByPkgName.isEmpty()) {
                    throw new IOException("Modules not found in " + layerDir);
                }
            }
        }
        try (Stream<Path> modules = Files.list(modulesDir)) {
            final Iterator<Path> i = modules.iterator();
            while (i.hasNext()) {
                final Path moduleDir = i.next();
                if (!moduleDir.getFileName().toString().equals("system")) {
                    findModules(moduleDir, moduleXmlByPkgName);
                    if (moduleXmlByPkgName.isEmpty()) {
                        throw new IOException("Modules not found in " + moduleDir);
                    }
                }
            }
        }
    }

    static void findModules(Path modulesDir, Map<String, Path> moduleXmlByPkgName) throws IOException {
        Files.walkFileTree(modulesDir, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                final Path moduleXml = dir.resolve("module.xml");
                if (!Files.exists(moduleXml)) {
                    return FileVisitResult.CONTINUE;
                }

                String packageName;
                if (moduleXml.getParent().getFileName().toString().equals("main")) {
                    packageName = modulesDir.getParent().relativize(moduleXml.getParent().getParent()).toString();
                } else {
                    packageName = modulesDir.getParent().relativize(moduleXml.getParent()).toString();
                }
                packageName = packageName.replace(File.separatorChar, '.');
                moduleXmlByPkgName.put(packageName, moduleXml);
                return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String trimPathToModulesDir(String modulePath) {
        int index = modulePath.indexOf(File.pathSeparator);
        return index == -1 ? modulePath : modulePath.substring(0, index);
    }

    private static ModuleLoader setupModuleLoader(final String modulePath) {
        assert modulePath != null : "modulePath not null";

        // verify the first element of the supplied modules path exists, and if it does not, stop and allow the user to correct.
        // Once modules are initialized and loaded we can't change Module.BOOT_MODULE_LOADER (yet).
        final Path moduleDir = Paths.get(trimPathToModulesDir(modulePath));
        if (Files.notExists(moduleDir) || !Files.isDirectory(moduleDir)) {
            throw new RuntimeException("The first directory of the specified module path " + modulePath + " is invalid or does not exist.");
        }

        // Set up sysprop env
        System.setProperty(SYSPROP_KEY_MODULE_PATH, modulePath);
        // Get the module loader
        return Module.getBootModuleLoader();

    }
}
