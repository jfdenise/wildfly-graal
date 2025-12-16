package launcher;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.json.JSONArray;
import org.json.JSONObject;

public class Launcher {

    private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
    private static final String SYSPROP_KEY_SYSTEM_PACKAGES = "jboss.modules.system.pkgs";
    // Those maps are used by WildFLy to resolve modules at runtime,
    // We don't have JBoss Modules classloaders at execution time.
    // Hack to be properly integrated.
    public static Map<String, Module> modules = new HashMap<>();
    static Module mainModule;

    static {
        new Launcher().hello();
        try {
            //System.setProperty("org.wildfly.graal", "true");
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
            Path p = Paths.get("min-core-server/standalone/configuration/logging.properties");
            //System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
            System.setProperty("logging.configuration", p.toUri().toString());
            System.setProperty("jboss.home.dir", Paths.get("min-core-server").toAbsolutePath().toString());
            System.setProperty("jboss.server.base.dir", Paths.get("min-core-server/standalone").toAbsolutePath().toString());

            Path modulesDir = Paths.get("min-core-server/modules").toAbsolutePath();
            LocalModuleLoader loader = (LocalModuleLoader) setupModuleLoader(modulesDir.toString());

            Map<String, Path> all = new HashMap<>();
            // Load all modules to have them accessible at runtime, and register as ParrallelCapable.
            handleModules(modulesDir, all);
            Path cache = Paths.get("min-core-server/jboss-modules-store");
            for (String k : all.keySet()) {
                System.out.println("Load module " + k);
                try {
                    Module mod = loader.loadModule(k);
                    if (k.equals("org.jboss.as.standalone")) {
                        mainModule = mod;
                    }
                    if (k.equals("org.wildfly.extension.undertow")) {
                        Class<?> clazz = mod.getClassLoader().loadClass("io.undertow.servlet.core.DeploymentManagerImpl");
                    }

                    //if(!k.equals("org.jboss.logmanager")) {
                    //}
//                    if (k.equals("org.wildfly.extension.io")) {
//                        mod = loader.loadModule(k);
//                        mod.preLoadServices();
//                    }
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
                    modules.put(k, mod);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.out.println("EX " + ex);
                }
            }
            Path dumpedServices = Paths.get("jboss-modules-recorded-services");
            for (String k : modules.keySet()) {
                if ("org.jboss.logmanager".equals(k)) {
                    continue;
                }
                Module mod = modules.get(k);
                Path services = dumpedServices.resolve(k).resolve("services.txt");
                if (Files.exists(services)) {
                    List<String> lst = Files.readAllLines(services);
                    Set<String> seen = new HashSet<>();
                    for (String serviceClass : lst) {
                        if (seen.contains(serviceClass)) {
                            continue;
                        }
                        seen.add(serviceClass);
                        if (!serviceClass.startsWith("java.lang.")) {
                            try {
                                mod.addServiceToCache(serviceClass);
                            } catch (Exception ex) {
                                //System.out.println("ERROR ADD SERVICE " + serviceClass + " for " + k + "EX " + ex);
                            }
                        }
                    }
                }
            }
            mainModule.preRun(new String[0]);
            Path dumpedClasses = Paths.get("jboss-modules-recorded-classes");
            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            modules.get("org.wildfly.extension.undertow").addClassToCache("org.apache.jasper.compiler.JspRuntimeContext");
            modules.get("org.wildfly.extension.undertow").addClassToCache("org.apache.jasper.servlet.JspServlet");
            modules.get("org.wildfly.extension.undertow").addClassToCache("org.wildfly.extension.undertow.deployment.JspInitializationListener");
            modules.get("org.wildfly.extension.undertow").addClassToCache("io.undertow.servlet.handlers.DefaultServlet");
            modules.get("deployment.helloworld.war").addClassToCache("jakarta.servlet.jsp.jstl.tlv.PermittedTaglibsTLV");
            modules.get("deployment.helloworld.war").addClassToCache("org.jboss.as.quickstarts.helloworld.HelloWorldServlet");
            modules.get("deployment.helloworld.war").populateClasses(dumpedClasses);
            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            
            JSONObject jo = new JSONObject(new String(Files.readAllBytes(Paths.get("reflective-dump").resolve("reachability-metadata.json"))));
            JSONArray reflection = (JSONArray) jo.get("reflection");
            Map<String, JSONObject> reflectionMap = new HashMap<>();
            Iterator<?> it = reflection.iterator();
//            while (it.hasNext()) {
//                JSONObject obj = (JSONObject) it.next();
//                String name = (String) obj.get("type");
//                if (obj.has("methods")) {
//                    reflectionMap.put(name, obj);
//                }
//            }
            System.out.println(reflection.getClass());
            for (String k : modules.keySet()) {
                if ("org.jboss.logmanager".equals(k)) {
                    continue;
                }
                Module m = modules.get(k);
                m.cleanupPermissions();
//                if ("io.undertow.servlet".equals(k)) {
//                    continue;
//                }
//                
//                if ("org.wildfly.transaction.client".equals(k)) {
//                    continue;
//                }
//                if ("io.undertow.websocket".equals(k)) {
//                    continue;
//                }
//                if ("org.jboss.msc".equals(k)) {
//                    continue;
//                }
                Module mod = modules.get(k);
                //mod.populateClasses(dumpedClasses);
                
                Set<String> set = mod.getClassesFromCache();
                for (String clazz : set) {
                    JSONObject reflectiveType = reflectionMap.get(clazz);
                    if (reflectiveType != null) {
                        //System.out.println("SPECIFIC REFLECTION FOR " + clazz);
                        JSONArray methods = (JSONArray) reflectiveType.get("methods");
                        if (methods != null) {
                            Iterator<?> itMethods = methods.iterator();
                            Class<?> loadedClass = mod.getClassFromCache(clazz);
                            while (itMethods.hasNext()) {
                                JSONObject method = (JSONObject) itMethods.next();
                                String name = method.getString("name");
                                if (name.equals("<init>")) {
                                    JSONArray args = (JSONArray) method.get("parameterTypes");
                                    List<Class<?>> params = new ArrayList<>();
                                    Iterator<?> itArgs = args.iterator();
                                    StringBuilder key = new StringBuilder();
                                    key.append(clazz);
                                    while (itArgs.hasNext()) {
                                        String t = (String) itArgs.next();
                                        params.add(mod.getClassLoader().loadClass(t));
                                        key.append("_" + t);
                                    }
                                    Class<?>[] arr = new Class[params.size()];
                                    arr = params.toArray(arr);
                                    //System.out.println("ADD CONSTRUCTOR " + key);
                                    try {
                                    Constructor c = loadedClass.getConstructor(arr);
                                    mod.addConstructorToCache(key.toString(), c);
                                    } catch(Exception ex) {
                                        System.out.println("ERROR Adding ctr " + ex);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void hello() {
        System.out.println("ORIGINAL");
    }

    public static void main(String[] args) throws Exception {
        new Launcher().hello();
        System.setProperty("org.wildfly.graal", "true");
        System.setProperty("jboss.home.dir", Paths.get("min-core-server").toAbsolutePath().toString());
        System.setProperty("user.home", Paths.get("/users/foo").toAbsolutePath().toString());
        System.setProperty("java.home", Paths.get("/tmp/java").toAbsolutePath().toString());
        System.out.println("Running Main entry point");
        for (String k : modules.keySet()) {
            Module m = modules.get(k);
            m.restorePermissions();
        }
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
            String custompackages = System.getProperty(SYSPROP_KEY_SYSTEM_PACKAGES);
            if (custompackages != null) {
                packages.append(",").append(custompackages);
            }
            // The ones that are in the classpath
            packages.append(",com.sun.el,jakarta.el");
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
