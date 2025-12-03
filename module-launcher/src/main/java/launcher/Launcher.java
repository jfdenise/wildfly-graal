package launcher;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
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
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

public class Launcher {

    private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
    private static final String SYSPROP_KEY_SYSTEM_MODULES = "jboss.modules.system.pkgs";
    // Those maps are used by WildFLy to resolve modules at runtime,
    // We don't have JBoss Modules classloaders at execution time.
    // Hack to be properly integrated.
    public static Map<String, Module> modules = new HashMap<>();
    public static Map<String, Module> callerModules = new HashMap<>();
    static Module mainModule;

    static {
        try {
            System.setProperty("org.wildfly.graal", "true");
        final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class);
        Iterator<Provider> iterator = providerServiceLoader.iterator();
        for (;;) {
            if (!(iterator.hasNext())) {
                break;
            }
            try {
                final Provider provider = iterator.next();
                
                if(!provider.getClass().getName().contains("org.bouncycastle")) {
                    System.out.println(provider.getClass().getName());
                    Security.addProvider(provider);
                } else {
                    System.out.println("DO NOT register " + provider.getClass().getName());
                }
            } catch (Throwable ex) {
                System.out.println("ERROR LOADING Provider " + ex);
            }
        }
            System.setProperty("org.jboss.boot.log.file", Paths.get("min-server2/standalone/log/server.log").toAbsolutePath().toString());
            Path p = Paths.get("min-server2/standalone/configuration/logging.properties");
            //System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
            System.setProperty("logging.configuration", p.toUri().toString());
            System.setProperty("jboss.home.dir", Paths.get("min-server2").toAbsolutePath().toString());
            System.setProperty("jboss.server.base.dir", Paths.get("min-server2/standalone").toAbsolutePath().toString());

            Path modulesDir = Paths.get("min-server2/modules").toAbsolutePath();
            LocalModuleLoader loader = (LocalModuleLoader) setupModuleLoader(modulesDir.toString());

            Map<String, Path> all = new HashMap<>();
            // Load all modules to have them accessible at runtime.
            handleModules(modulesDir, all);
            for (String k : all.keySet()) {
                System.out.println("Load module " + k);
                try {
                    Module mod = null;
                    if (k.equals("org.jboss.as.standalone")) {
                        mod = loader.loadModule(k);
                        mainModule = mod;
                    }
//                    if (k.equals("org.jboss.as.controller")) {
//                        System.out.println("LOADING controller");
//                        mod = loader.loadModule(k);
//                        mod.getClassLoader().loadClass("org.jboss.as.controller.persistence.ConfigurationExtensionFactory");
//                    }
//                    if (k.equals("org.jboss.msc")) {
//                        System.out.println("LOADING MSC 2");
//                        mod = loader.loadModule(k);
//                        mod.getClassLoader().loadClass("org.jboss.msc.service.ServiceContainerImpl");
//                        Class clazz = mod.getClassLoader().loadClass("org.jboss.msc.service.ServiceLogger_$logger", false);
//                        System.out.println("CLASS " + clazz.getCanonicalName() + " classloader " + clazz.getClassLoader());
//                    }
                    if(mod != null) {
                    modules.put(k, mod);
                    }
                } catch (Exception ex) {
                    System.out.println("EX " + ex);
                }
            }
            mainModule.preRun(new String[0]);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("org.wildfly.graal", "true");
        System.setProperty("jboss.home.dir", Paths.get("min-server2").toAbsolutePath().toString());
        System.setProperty("user.home", Paths.get("/users/foo").toAbsolutePath().toString());
        System.setProperty("java.home", Paths.get("/tmp/java").toAbsolutePath().toString());
        System.out.println("Running Main entry point");
        mainModule.run(args);
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
