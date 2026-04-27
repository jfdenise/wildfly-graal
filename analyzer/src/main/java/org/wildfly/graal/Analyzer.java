package org.wildfly.graal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayers;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayersBuilder;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.Layer;
import org.wildfly.glow.ProvisioningTracker;
import org.wildfly.glow.ScanArguments;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.maven.MavenResolver;

public class Analyzer {

    private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
    private static final String SYSPROP_KEY_SYSTEM_PACKAGES = "jboss.modules.system.pkgs";

    public static void main(String[] args) throws Exception {
        Path output = Paths.get("analyzer-output");
        Files.createDirectories(output);

        Map<String, Path> all = new HashMap<>();

        Path deploymentPath = Paths.get(args[0]).toAbsolutePath();
        String addOnsString = args.length == 3 ? args[2] : null;
        Set<String> addOns = new HashSet<>();
        if (addOnsString != null) {
            String[] arr = addOnsString.split(",");
            for(String s : arr) {
                addOns.add(s);
            }
        }
        Set<String> supportedLayers;
        try (InputStream stream = Analyzer.class.getClassLoader().getResourceAsStream("supported-layers.txt")) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                supportedLayers = new HashSet<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    supportedLayers.add(line);
                }

            }
        }
        Set<String> bannedLayers;
        try (InputStream stream = Analyzer.class.getClassLoader().getResourceAsStream("banned-layers.txt")) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                bannedLayers = new HashSet<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    bannedLayers.add(line);
                }
            }
        }
        Set<String> requiredLayers;
        try (InputStream stream = Analyzer.class.getClassLoader().getResourceAsStream("required-layers.txt")) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                requiredLayers = new HashSet<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    requiredLayers.add(line);
                }
            }
        }
        Path provisioningFile = Files.createTempFile("graal-glow", null);
        provisioningFile.toFile().deleteOnExit();
        try (InputStream stream = Analyzer.class.getClassLoader().getResourceAsStream("provisioning.xml")) {
            Files.copy(stream, provisioningFile, StandardCopyOption.REPLACE_EXISTING);
        }
        
        Path cliFile = output.resolve("graal-adjustments.cli");
        try (InputStream stream = Analyzer.class.getClassLoader().getResourceAsStream("graal-adjustments.cli")) {
            Files.copy(stream, cliFile, StandardCopyOption.REPLACE_EXISTING);
        }
        
        Path debugFile = output.resolve("graal-traces.cli");
        try (InputStream stream = Analyzer.class.getClassLoader().getResourceAsStream("graal-traces.cli")) {
            Files.copy(stream, debugFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Check with Glow that it doesn't require unsupported layers
        List<Path> deployments = new ArrayList<>();
        deployments.add(deploymentPath);
        ScanArguments.Builder builder = Arguments.scanBuilder();
        builder.setBinaries(deployments);
        builder.setJndiLayers(requiredLayers);
        builder.setProvisoningXML(provisioningFile);
        builder.setUserEnabledAddOns(addOns);
        MavenRepoManager repoManager = MavenResolver.newMavenResolver();
        ScanResults res = GlowSession.scan(repoManager, builder.build(), GlowMessageWriter.DEFAULT);
        Set<Layer> layers = res.getDiscoveredLayers();
        for(Layer l : layers) {
            if (!supportedLayers.contains(l.getName())) {
                throw new Exception("Layer " + l.getName() + " is not supported. "
                        + "You can't build a WildFly native launcher.");
            }
        }
        System.out.println("Set of discovered layers:");
        res.outputCompactInformation();
        System.out.println("Provisioning the server:");
        // Provision the server
        GalleonProvisioningConfig.Builder provisioningConfigBuilder = GalleonProvisioningConfig.builder();
        ConfigId id = new ConfigId("standalone", "standalone.xml");
        GalleonConfigurationWithLayers original = res.getProvisioningConfig().getDefinedConfig(id);
        GalleonConfigurationWithLayersBuilder configBuilder = GalleonConfigurationWithLayersBuilder.builder(original);
        configBuilder.setName(id.getName());
        configBuilder.setModel(id.getModel());
        for (GalleonFeaturePackConfig fp : res.getProvisioningConfig().getFeaturePackDeps()) {
            provisioningConfigBuilder.addFeaturePackDep(fp);
        }
        for (String l : bannedLayers) {
            configBuilder.excludeLayer(l);
        }
        provisioningConfigBuilder.addConfig(configBuilder.build());
        for (Entry<String, String> entry : res.getProvisioningConfig().getOptions().entrySet()) {
            provisioningConfigBuilder.addOption(entry.getKey(), entry.getValue());
        }
        provisioningConfigBuilder.addOption("ignore-not-excluded-layers", "true");
        Path jbossHome = output.resolve("wildfly-server");
        if (Files.exists(jbossHome)) {
            IoUtils.recursiveDelete(jbossHome);
        }
        GalleonProvisioningConfig newConfig = provisioningConfigBuilder.build();
        provisionServer(newConfig, jbossHome, repoManager, GlowMessageWriter.DEFAULT);

        // Update the logging file
        Path loggingFile = jbossHome.resolve("standalone").resolve("configuration").resolve("logging.properties");
        try (InputStream stream = Analyzer.class.getClassLoader().getResourceAsStream("logging.properties")) {
            Files.copy(stream, loggingFile, StandardCopyOption.REPLACE_EXISTING);
        }

        Path modulesDir = jbossHome.resolve("modules").toAbsolutePath();
        LocalModuleLoader loader = (LocalModuleLoader) setupModuleLoader(modulesDir.toString());
        handleModules(modulesDir, all);
        Set<String> sorted = new TreeSet<>();
        
        Path allPackages = output.resolve("allServerPackages.txt");
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
        Path properties = Paths.get(args[1]);

        Properties props = new Properties();
        try (FileInputStream stream = new FileInputStream(properties.toFile())) {
            props.load(stream);
        }

        DeploymentScanner scanner = new DeploymentScanner(deploymentPath, false, Collections.emptySet(), props);
        Set<String> allClasses = new TreeSet<>();
        Set<String> jsonBClasses = new TreeSet<>();
        Set<String> cdiClasses = new TreeSet<>();
        Set<String> cdiProxyClasses = new TreeSet<>();
        scanner.scan(allClasses, jsonBClasses, cdiClasses, cdiProxyClasses);
        Path deploymentClasses = output.resolve("allDeploymentClasses.txt");
        Files.deleteIfExists(deploymentClasses);
//        for (String s : allClasses) {
//            System.out.println(s);
//        }
        System.out.println("Deployment class names stored in " + deploymentClasses);
        Path jsonClasses = output.resolve("allJsonBindingClasses.txt");
        Files.deleteIfExists(jsonClasses);
        Files.write(deploymentClasses, allClasses, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (!jsonBClasses.isEmpty()) {

            System.out.println("JSON Binding class names stored in " + jsonClasses);
            Files.write(jsonClasses, jsonBClasses, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Path cdiClassesFile = output.resolve("allCDIClasses.txt");
        Files.deleteIfExists(cdiClassesFile);
        // Write CDI classes
        if (!cdiClasses.isEmpty()) {
            System.out.println("CDI class names stored in " + cdiClassesFile);
            Files.write(cdiClassesFile, cdiClasses, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Path cdiProxiesFile = output.resolve("allCDIProxyClasses.txt");
        Files.deleteIfExists(cdiProxiesFile);
        // Write CDI classes
        if (!cdiProxyClasses.isEmpty()) {
            System.out.println("CDI proxy class names stored in " + cdiProxiesFile);
            Files.write(cdiProxiesFile, cdiProxyClasses, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Path resourcesFile = output.resolve("resources.txt");
        Files.deleteIfExists(resourcesFile);
        String resourcesProp = props.getProperty("preloaded.resources");
        if (resourcesProp != null) {
            Set<String> resources = new HashSet<>();
            String[] split = resourcesProp.split(",");
            for (String s : split) {
                s = s.trim();
                if (!s.isEmpty()) {
                    resources.add(s);
                }
            }
            Files.write(resourcesFile, resources, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
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

    static void provisionServer(GalleonProvisioningConfig config, Path home, MavenRepoManager resolver, GlowMessageWriter writer) throws ProvisioningException {
        try (Provisioning pm = new GalleonBuilder().addArtifactResolver(resolver).newProvisioningBuilder(config)
                .setInstallationHome(home)
                .setLogTime(false)
                .setMessageWriter(new MessageWriter() {
                    @Override
                    public void verbose(Throwable cause, CharSequence message) {
                        if (writer.isVerbose()) {
                            writer.trace(message);
                        }
                    }

                    @Override
                    public void print(Throwable cause, CharSequence message) {
                        writer.info(message);
                    }

                    @Override
                    public void error(Throwable cause, CharSequence message) {
                        writer.error(message);
                    }

                    @Override
                    public boolean isVerboseEnabled() {
                        return writer.isVerbose();
                    }

                    @Override
                    public void close() throws Exception {
                    }

                })
                .setRecordState(true)
                .build()) {
            ProvisioningTracker.initTrackers(pm, writer);

            pm.provision(config);
        }
    }
}
