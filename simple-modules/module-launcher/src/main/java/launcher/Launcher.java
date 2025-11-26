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
import java.util.Map;
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
    public static Module mainModule;
    static {
        try {
            // Required to avoid JBoss Modules content incompatible with Graal.
            System.setProperty("org.wildfly.graal", "true");
            //Module.setModuleLogger(new StreamModuleLogger(System.out));
            Path modulesDir = Paths.get("modules").toAbsolutePath();
            LocalModuleLoader loader = (LocalModuleLoader) setupModuleLoader(modulesDir.toString());
            System.out.println("Module loader " + loader);
            
            Map<String, Path> all = new HashMap<>();
            // Load all modules to have them accessible at runtime.
            findModules(modulesDir, all);
            for (String k : all.keySet()) {
                try {
                    Module mod = loader.loadModule(k);
                    if (k.equals("hello")) {
                        mainModule = mod;
                    }
                    modules.put(k, mod);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.out.println("EX " + ex);
                }
            }
            System.out.println("Running Main entry point");
            mainModule.run(new String[0]);
            System.out.println("FINISH PRE-RUN");
        } catch (Exception ex) {
           throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) throws Exception {
        mainModule.postRun();
    }

    static void handleModules(Path modulesDir, Map<String, Path> moduleXmlByPkgName) throws IOException {
        final Path layersDir = modulesDir;
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
