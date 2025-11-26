# Simple examples:

To highlight limitation at build time. No dynamic classloading can be shared between build time and runt time.

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

Produce the metadata to be able to load the Hello class at runtime.

java -agentlib:native-image-agent=config-output-dir=./graal-config,experimental-class-define-support=true -cp ../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar launcher.Launcher

native-image --enable-url-protocols=jar,data -H:ConfigurationFileDirectories=graal-config -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp ../jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:hello/target/HelloModule-1.0-SNAPSHOT.jar:api/target/APIModule-1.0-SNAPSHOT.jar:provider/target/ProviderModule-1.0-SNAPSHOT.jar --initialize-at-build-time=org.jboss.logmanager,launcher.Launcher,org.jboss.modules

### Run the image

./ModuleLauncher-1.0-SNAPSHOT

Expect failure: The provider is null.
Rational:
1) I added a postRun method to JBoss Modules Module API that mimics run, the mainClass is expected to have a postRun static method. Module.run would be called in static init, postRun at runtime.

2) I have a simple JBoss Modules example (3 modules api, consumer, provider). consumer is the main module.
Its main class main method (called by run), load a provider and set it in a static field in its class. This would represent the WildFly initalization (:-)) done at build time.
In its postRun method it accesses the static provider to print Hello World (That would be the WildFly last step startup).

3) Now the launcher. In static init it loads the main module, put it in a static field and call module.run.
In the main method it calls module.postRun.

That works well in java. I run java with the experimental-class-define-support to dump the defined classes that will be used to build the image.

I build the native image, with the launcher and org.jboss.modules initialized at build time. Nothing is in the classpath. I rely on the dumped defined classes. Image build well.

I start the image:
The postRun is called, but the provider is null.

I understand the problem I think. At build time we used JBoss Modules class loading. At runtime we use the class from the classPath (loaded from the dumped defined classes).