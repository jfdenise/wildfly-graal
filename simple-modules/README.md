# Simple examples:

WARNING, requires: https://github.com/jfdenise/jboss-modules/pull/new/2.x-graal-post-run

Introduce a postRun method in the JBoss Modules API to be able to call the module initialized in the image heap at runtime.

* Build api, provider, hello and module-launcher

```
cp hello/target/HelloModule-1.0-SNAPSHOT.jar modules/hello/main/hello.jar
cp provider/target/ProviderModule-1.0-SNAPSHOT.jar modules/provider/main/provider.jar
cp api/target/APIModule-1.0-SNAPSHOT.jar modules/api/main/api.jar
```

## Run it with java JBoss Modules:

* java  -cp ../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar launcher.Launcher

## Run with JPMS

* java --module-path hello/target/HelloModule-1.0-SNAPSHOT.jar:api/target/APIModule-1.0-SNAPSHOT.jar:provider/target/ProviderModule-1.0-SNAPSHOT.jar --module HelloModule

## Build native image with JBoss Modules (JAR + CP)

native-image --enable-url-protocols=jar,data -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp ../../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:hello/target/HelloModule-1.0-SNAPSHOT.jar:provider/target/ProviderModule-1.0-SNAPSHOT.jar --initialize-at-build-time=org.jboss.logmanager,launcher.Launcher,org.jboss.modules,hello.Hello,api.Provider,provider.ProviderImpl --enable-sbom=false

### Run the image

./ModuleLauncher-1.0-SNAPSHOT