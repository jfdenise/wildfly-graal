package launcher;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
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
import java.util.stream.Stream;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.ModularContentHandlerFactory;
import org.jboss.modules.ModularURLStreamHandlerProvider;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.log.StreamModuleLogger;

public class Launcher {

    private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
    private static final String SYSPROP_KEY_SYSTEM_MODULES = "jboss.modules.system.pkgs";
    //private static Module helloModule;
    public static Map<String, Module> modules = new HashMap<>();
    public static Map<String, Module> callerModules= new HashMap<>();
    static {
        //Module.setModuleLogger(new StreamModuleLogger(System.out));
        try {
        // Force loading of this class that must be initialized
         //   Class.forName("org.wildfly.common._private.CommonMessages");
//        Path modulesDir = Paths.get("modules").toAbsolutePath();
//        LocalModuleLoader loader = (LocalModuleLoader) setupModuleLoader(modulesDir.toString());
//        System.out.println("LOCAL LOADER + DEBUG ENABLED" + loader);
//        helloModule = loader.loadModule("hello");
//
//            
//            Map<String, Path> all = new HashMap<>();
//            handleModules(modulesDir, all);
//            for (String k : all.keySet()) {
//                try {
//                    System.out.println("Load module " + k);
//                    Module mod = loader.loadModule(k);
//                    if (k.equals("org.jboss.as.standalone")) {
//                        helloModule = mod;
//                    }
//                    modules.put(k, mod);
//                } catch (Exception ex) {
//                    System.out.println("EX " + ex);
//                }
//            }
try {
                    System.out.println("REGISTER DATA PROTOCOL");
                    URL.setURLStreamHandlerFactory(ModularURLStreamHandlerProvider.INSTANCE);
                } catch (Throwable t) {
                    t.printStackTrace();
                    System.out.println("ERROR WHEN REGISTERING");
                    // todo log a warning or something
                }
                try {
                    URLConnection.setContentHandlerFactory(ModularContentHandlerFactory.INSTANCE);
                } catch (Throwable t) {
                    // todo log a warning or something
                }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("EX " + ex);
        }
    }

    static void handleModules(Path modulesDir, Map<String, Path> moduleXmlByPkgName) throws IOException {
        final Path layersDir = modulesDir.resolve("system").resolve("layers");
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
                    packageName = modulesDir.relativize(moduleXml.getParent().getParent()).toString();
                } else {
                    packageName = modulesDir.relativize(moduleXml.getParent()).toString();
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

    public static void main(String[] args) throws Exception {
        final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class);
            Iterator<Provider> iterator = providerServiceLoader.iterator();
            for (;;) {
                if (!(iterator.hasNext())) {
                    break;
                }
                try {
                final Provider provider = iterator.next();
                
                //if (provider.getName().startsWith("WildFly")) {
                    System.out.println(provider.getClass().getName());
                    //if(provider.getClass().getName().equals("org.wildfly.security.WildFlyElytronHttpDigestProvider")) {
                    //providers.add(provider);
                    Security.addProvider(provider);
                    //}
                //}
                } catch(Throwable ex) {
                    System.out.println("ERROR LOADING Provider " + ex);
                }
            }
        System.setProperty("org.jboss.boot.log.file",Paths.get("min-server2/standalone/log/server.log").toAbsolutePath().toString());
        Path p = Paths.get("min-server2/standalone/configuration/logging.properties");
        System.out.println("Setting java.util.logging.manager=org.jboss.logmanager.LogManager");
        //System.setProperty("java.util.logging.manager","org.jboss.logmanager.LogManager");
        System.setProperty("logging.configuration",p.toUri().toString());
        System.setProperty("jboss.home.dir", Paths.get("min-server2").toAbsolutePath().toString());
        System.setProperty("user.home", Paths.get("/users/jdenise").toAbsolutePath().toString());
        System.setProperty("java.home", Paths.get("/tmp/java").toAbsolutePath().toString());
        System.setProperty("jboss.server.base.dir", Paths.get("min-server2/standalone").toAbsolutePath().toString());
//Module.setModuleLogger(new StreamModuleLogger(System.out));

        Path modulesDir = Paths.get("min-server2/modules").toAbsolutePath();
        LocalModuleLoader loader = (LocalModuleLoader) setupModuleLoader(modulesDir.toString());
        Module helloModule = null;
        Map<String, Path> all = new HashMap<>();
            handleModules(modulesDir, all);
            for (String k : all.keySet()) {
                try {
                    Module mod = loader.loadModule(k);
                    //Module mod = loader.loadModule(k);
                    if (k.equals("org.jboss.as.standalone")) {
                        System.out.println("Load module " + k);
                        
                        helloModule = mod;
                    }
                    modules.put(k, mod);
                } catch (Exception ex) {
                    System.out.println("EX " + ex);
                }
            }
        System.out.println("LOCAL LOADER + DEBUG ENABLED" + loader);
        //Module helloModule = loader.loadModule("hello");
        helloModule.run(args);

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

            final StringBuilder packages = new StringBuilder("org.jboss.modules");
            String custompackages = System.getProperty(SYSPROP_KEY_SYSTEM_MODULES);
            if (custompackages != null) {
                packages.append(",").append(custompackages);
            }
            System.setProperty(SYSPROP_KEY_SYSTEM_MODULES, packages.toString());

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
