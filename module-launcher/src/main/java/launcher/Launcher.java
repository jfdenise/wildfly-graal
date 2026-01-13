package launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

public class Launcher {

    private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
    // Those maps are used by WildFLy to resolve modules at runtime,
    // We don't have JBoss Modules classloaders at execution time.
    // Hack to be properly integrated.
    public static Map<String, Module> modules = new HashMap<>();
    static Module mainModule;
    private static Provider[] providers;
    static {
        try {
            String deploymentModule = System.getProperty("org.wildfly.graal.deployment.module");

            // No more needed, although how the classes are seen is not understood.
            // XXX Those classes would have to be discovered by a previous phase (e.g: dumped in a file that this loauncher would read.
            // We add them here because they are not recorded with the internal run at build time, not seen by Graal compiler. 
            // They are classes loaded on receive of an async event, so nothing links to them.
            // Other classes are recorded at build time thanks to the org.jboss.modules.record.classes that defines the deployment packages to be recorded
            // All the recording and these classes should be removed once we have the tool that discover the application classes.
            String[] depClasses = {
                "org.jboss.quickstarts.websocket.model.Bid",
                "org.jboss.quickstarts.websocket.model.BidStatus",
                "org.jboss.quickstarts.websocket.model.Bidding",
                "org.jboss.quickstarts.websocket.model.Bidding$1",
                "org.jboss.quickstarts.websocket.model.BiddingFactory",
                "org.jboss.quickstarts.websocket.model.Item"
            };
            StringBuilder classesBuilder = new StringBuilder();
            for (String s : depClasses) {
                if (!classesBuilder.isEmpty()) {
                    classesBuilder.append(",");
                }
                classesBuilder.append(s);

            }
            // Will be consumed by the created Deployment Module
            // This appears to be no more required...All classes are seen,
            // root cause not understood!
            //System.setProperty("org.wildfly.graal.deployment.classes", classesBuilder.toString());

            System.setProperty("org.wildfly.graal.build.time", "true");
            // We want to record the classses that are loaded by the Deployment module classloader
            System.setProperty("org.jboss.modules.record.classes.of", deploymentModule);
            final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class);
            Iterator<Provider> iterator = providerServiceLoader.iterator();
            for (;;) {
                if (!(iterator.hasNext())) {
                    break;
                }
                try {
                    final Provider provider = iterator.next();

                    if (!provider.getClass().getName().contains("org.bouncycastle")) {
                        System.out.println(provider.getClass().getName());
                        Security.addProvider(provider);
                    } else {
                        System.out.println("DO NOT register " + provider.getClass().getName());
                    }
                } catch (Throwable ex) {
                    System.out.println("ERROR LOADING Provider " + ex);
                }
            }
            System.setProperty("org.jboss.boot.log.file", Paths.get("min-core-server/standalone/log/server.log").toAbsolutePath().toString());
            System.setProperty("jboss.home.dir", Paths.get("min-core-server").toAbsolutePath().toString());
            System.setProperty("jboss.server.base.dir", Paths.get("min-core-server/standalone").toAbsolutePath().toString());
            System.setProperty("org.wildfly.graal.cache.class", "launcher.Cache");
            Path modulesDir = Paths.get("min-core-server/modules").toAbsolutePath();
            LocalModuleLoader loader = (LocalModuleLoader) setupModuleLoader(modulesDir.toString());

            Map<String, Path> all = new HashMap<>();
            // Load all modules to have them accessible at runtime, and register as ParrallelCapable.
            handleModules(modulesDir, all);
            System.setProperty("org.wildfly.graal.build.time.load.modules", "true");
            for (String k : all.keySet()) {
                System.out.println("Load module " + k);
                try {
                    Module mod = loader.loadModule(k);
                    Cache classCache = new Cache();
                    mod.setClassCache(classCache);
                    if (k.equals("org.jboss.as.standalone")) {
                        mainModule = mod;
                    }
                    for (String serviceClass : mod.getServices()) {
                        if (!serviceClass.startsWith("java.lang.")) {
                           Set<String> classes = mod.getCache().addServiceToCache(serviceClass);
                           if(k.equals("org.jboss.as.logging")) {
                               for(String c : classes) {
                                   c = c.replace("$", "\\\\\\$");
                                   System.out.println(c+"\\,");
                               }
                           }
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
            System.clearProperty("org.jboss.modules.record.classes.of");
//            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
//            System.out.println("The classes that we add to the cache");
//            modules.get("org.wildfly.extension.undertow").getCache().addClassToCache("org.apache.jasper.compiler.JspRuntimeContext");
//            modules.get("org.wildfly.extension.undertow").getCache().addClassToCache("org.apache.jasper.servlet.JspServlet");
//            modules.get("org.wildfly.extension.undertow").getCache().addClassToCache("org.wildfly.extension.undertow.deployment.JspInitializationListener");
//            modules.get("org.wildfly.extension.undertow").getCache().addClassToCache("io.undertow.servlet.handlers.DefaultServlet");
//
//            modules.get("io.undertow.websocket").getCache().addClassToCache("io.undertow.websockets.jsr.JsrWebSocketFilter");
//            modules.get("io.undertow.websocket").getCache().addClassToCache("io.undertow.websockets.jsr.JsrWebSocketFilter$LogoutListener");
//            modules.get("io.undertow.websocket").getCache().addClassToCache("io.undertow.websockets.jsr.Bootstrap$WebSocketListener");
//
//            modules.get("io.undertow.core").getCache().addClassToCache("io.undertow.server.DirectByteBufferDeallocator");
//            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");

            for (String k : modules.keySet()) {
                Module m = modules.get(k);
                m.cleanupPermissions();
            }
             for (Provider p : Security.getProviders()) {
            System.out.println("END OF BUILD PROVIDER " + p.getClass());
            for (Provider.Service s : p.getServices()) {
                System.out.println("SERVICE " + s.getClassName());
            }
        }
             providers = Security.getProviders();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) throws Exception {
        System.clearProperty("org.wildfly.graal.build.time");
        System.setProperty("org.wildfly.graal", "true");
        System.setProperty("jboss.home.dir", Paths.get("min-core-server").toAbsolutePath().toString());
        System.setProperty("user.home", Paths.get("/users/foo").toAbsolutePath().toString());
        System.setProperty("java.home", Paths.get("/tmp/java").toAbsolutePath().toString());
        System.out.println("Running Main entry point");
        for (String k : modules.keySet()) {
            Module m = modules.get(k);
            m.restorePermissions();
        }
        for (Provider p : providers) {
            Security.addProvider(p);
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

        final String classPath = System.getProperty(SYSPROP_KEY_CLASS_PATH);
        try {
            // Set up sysprop env
            System.clearProperty(SYSPROP_KEY_CLASS_PATH);
            System.setProperty(SYSPROP_KEY_MODULE_PATH, modulePath);
            // Get the module loader
            return Module.getBootModuleLoader();
        } finally {
            // Return to previous state for classpath prop
            if (classPath != null) {
                System.setProperty(SYSPROP_KEY_CLASS_PATH, classPath);
            }
        }
    }
}
