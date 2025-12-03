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

* java  -agentlib:native-image-agent=config-output-dir=./grall-config,experimental-class-define-support=true -cp ../../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar launcher.Launcher

## Run with JPMS

You can't, it will fail with : Package mod1 in both module Mod1V2Module and module Mod1V1Module

* java --module-path hello/target/HelloModule-1.0-SNAPSHOT.jar:api/target/APIModule-1.0-SNAPSHOT.jar:provider/target/ProviderModule-1.0-SNAPSHOT.jar --module HelloModule

## Build native image with JBoss Modules (JAR + CP)

native-image -H:ConfigurationFileDirectories=grall-config \ --enable-url-protocols=jar,data -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp ../../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar --initialize-at-build-time=launcher.Launcher,org.jboss.modules --enable-sbom=false
native-image -H:ConfigurationFileDirectories=grall-config --enable-url-protocols=jar,data -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp ../../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:modules/provider/main/provider.jar:modules/hello/main/hello.jar:modules/api/main/api.jar:modules/provider/main/provider.jar --initialize-at-build-time=launcher.Launcher,org.jboss.modules --enable-sbom=false

native-image --enable-url-protocols=jar,data -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp ../../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar --initialize-at-build-time=launcher.Launcher,org.jboss.modules --enable-sbom=false

native-image -H:ConfigurationFileDirectories=grall-config --enable-url-protocols=jar,data -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp ../../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar --initialize-at-build-time=launcher.Launcher,org.jboss.modules,org.jboss.logging,mod1.WarningLogger --enable-sbom=false

### Run the image

./ModuleLauncher-1.0-SNAPSHOT