# wildfly-graal

* Build api, provider, hello and module-launcher
* cp hello/target/HelloModule-1.0-SNAPSHOT.jar modules/hello/main/hello.jar
* cp provider/target/ProviderModule-1.0-SNAPSHOT.jar modules/provider/main/provider.jar
* cp api/target/APIModule-1.0-SNAPSHOT.jar modules/api/main/api.jar

## Run it with java JBoss Modules:

* java  -cp /Users/jdenise/.m2/repository/org/jboss/modules/jboss-modules/2.2.0.Final/jboss-modules-2.2.0.Final.jar:module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar launcher.Launcher

## Run with JPMS

* java --module-path hello/target/HelloModule-1.0-SNAPSHOT.jar:api/target/APIModule-1.0-SNAPSHOT.jar:provider/target/ProviderModule-1.0-SNAPSHOT.jar --module HelloModule


## Build native image no JBoss Modules, use of JPMS.

native-image --module-path hello/target/HelloModule-1.0-SNAPSHOT.jar:api/target/APIModule-1.0-SNAPSHOT.jar:provider/target/ProviderModule-1.0-SNAPSHOT.jar --module HelloModule  --add-modules APIModule,ProviderModule

### Run the image

./hellomodule

## Build native image with JBoss Modules (JAR + CP)

native-image -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp /Users/jdenise/.m2/repository/org/jboss/modules/jboss-modules/2.2.0.Final/jboss-modules-2.2.0.Final.jar:hello/target/HelloModule-1.0-SNAPSHOT.jar:api/target/APIModule-1.0-SNAPSHOT.jar:provider/target/ProviderModule-1.0-SNAPSHOT.jar --initialize-at-build-time launcher.Launcher

### Run the image

./launcher.Launcher
