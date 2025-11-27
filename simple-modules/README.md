# Simple examples:

WARNING, requires: https://github.com/jfdenise/jboss-modules/pull/new/2.x-graal-post-run

Introduce a postRun method in the JBoss Modules API to be able to call the module initialized in the image heap at runtime.

* Build api, provider, hello, md1_v1, mod2_v2 and module-launcher
* 2 classes of same name of different versions are put in the classpath.

```
cp hello/target/HelloModule-1.0-SNAPSHOT.jar modules/hello/main/hello.jar
cp provider/target/ProviderModule-1.0-SNAPSHOT.jar modules/provider/main/provider.jar
cp api/target/APIModule-1.0-SNAPSHOT.jar modules/api/main/api.jar
```

## Run it with java JBoss Modules:

* java  -cp ../../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar launcher.Launcher

## Run with JPMS

You can't, it will fail with : Package mod1 in both module Mod1V2Module and module Mod1V1Module

* java --module-path hello/target/HelloModule-1.0-SNAPSHOT.jar:api/target/APIModule-1.0-SNAPSHOT.jar:provider/target/ProviderModule-1.0-SNAPSHOT.jar --module HelloModule

## Build native image with JBoss Modules (JAR + CP)

native-image --enable-url-protocols=jar,data -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp ../../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:mod1_v1/target/Mod1V1Module-1.0-SNAPSHOT.jar:mod1_v2/target/Mod1V2Module-1.0-SNAPSHOT.jar:hello/target/HelloModule-1.0-SNAPSHOT.jar:provider/target/ProviderModule-1.0-SNAPSHOT.jar --initialize-at-build-time=org.jboss.logmanager,launcher.Launcher,org.jboss.modules,hello.Hello,api.Provider,provider.ProviderImpl,mod1.Version --enable-sbom=false

### Run the image

./ModuleLauncher-1.0-SNAPSHOT