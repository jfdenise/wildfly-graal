package org.wildfly.graal;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

public class Analyzer {

    private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
    private static final String SYSPROP_KEY_SYSTEM_PACKAGES = "jboss.modules.system.pkgs";

    public static void main(String[] args) throws Exception {
        Map<String, Path> all = new HashMap<>();
        String server = args[0];
        Path modulesDir = Paths.get(server).resolve("modules").toAbsolutePath();
        LocalModuleLoader loader = (LocalModuleLoader) setupModuleLoader(modulesDir.toString());
        handleModules(modulesDir, all);
        Set<String> sorted = new TreeSet<>();
        Path allPackages = Paths.get("allServerPackages.txt");
        for (String k : all.keySet()) {
            //System.out.println("Load module " + k);
            Module m = loader.loadModule(k);
            Set<String> p = m.getClassLoader().getLocalPaths();
            sorted.addAll(p);
        }
        sorted = cleanupSet(sorted);
        Files.deleteIfExists(allPackages);
//        for (String s : sorted) {
//            System.out.println(s);
//        }
        System.out.println("Server classes packages name stored in " + allPackages);
        Files.write(allPackages, sorted, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        //System.out.println(sorted.size());
        // Discover deployment classes
        String deployment = args[1];
        Path deploymentPath = Paths.get(deployment).toAbsolutePath();
        DeploymentScanner scanner = new DeploymentScanner(deploymentPath, false, Collections.emptySet());
        Set<String> allClasses = new TreeSet<>();
        scanner.scan(allClasses);
        Path deploymentClasses = Paths.get("allDeploymentClasses.txt");
        Files.deleteIfExists(deploymentClasses);
//        for (String s : allClasses) {
//            System.out.println(s);
//        }
        System.out.println("Deployment classe names stored in " + deploymentClasses);
        Files.write(deploymentClasses, allClasses, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static Set<String> cleanupSet(Set<String> set) {
        Set<String> sorted = new TreeSet<>();
        for (String s : set) {
            if (s.startsWith("META-INF") || s.startsWith("OSGI-INF") || !s.contains("/")) {
                continue;
            }
            s = s.replaceAll("/", ".");
            sorted.add(s);
        }
        return sorted;
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

            final StringBuilder packages = new StringBuilder("org.jboss.modules");
            String custompackages = System.getProperty(SYSPROP_KEY_SYSTEM_PACKAGES);
            if (custompackages != null) {
                packages.append(",").append(custompackages);
            }
            packages.append(",launcher,org.jboss.logmanager,org.jboss.logging");
            System.setProperty(SYSPROP_KEY_SYSTEM_PACKAGES, packages.toString());

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
