# wildfly-graal

# WildFly core

# Build dependencies

* galleon install wildfly-core --layers=base-server,-git-history --dir=min-server2
* clone and build:  https://github.com/jfdenise/jboss-modules/pull/new/2.x-graal-poc
* cd module-launcher
* mvn clean install

## Prepare the built image

* Run : JAVA_OPTS="-agentlib:native-image-agent=config-output-dir=./min-server-graal-agent2,experimental-class-define-support=true" sh ./min-server2/bin/standalone.sh
* Kill the server

## Build the image

In this script replace some jdenise with your context. We do compute the classpath dynamically 

* Generate the script: sh ./generate-build-script.sh

* Do the buid: sh ./build-image.sh

## Run the image

* ./ModuleLauncher-1.0-SNAPSHOT


# Simple examples:

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

# Attempt to remove classes from the classpath and add discovered ones

* Run the application with options to track defined classes:

java -agentlib:native-image-agent=config-output-dir=./graal-agent,experimental-class-define-support=true  -cp /Users/jdenise/.m2/repository/org/jboss/modules/jboss-modules/2.2.0.Final/jboss-modules-2.2.0.Final.jar:module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar launcher.Launcher

* Build the image provising the dumped configuration and no classes in the 

native-image -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp /Users/jdenise/.m2/repository/org/jboss/modules/jboss-modules/2.2.0.Final/jboss-modules-2.2.0.Final.jar --initialize-at-build-time launcher.Launcher -H:ConfigurationFileDirectories=graal-agent  

## Wildfly core

JAVA_OPTS="-agentlib:native-image-agent=config-output-dir=./min-server-graal-agent2,experimental-class-define-support=true" sh ./min-server2/bin/standalone.sh

native-image -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp /Users/jdenise/.m2/repository/org/jboss/modules/jboss-modules/2.3.0.Final-SNAPSHOT/jboss-modules-2.3.0.Final-SNAPSHOT.jar --initialize-at-build-time=launcher.Launcher,org.jboss.modules -H:ConfigurationFileDirectories=min-server-graal-agent   --enable-url-protocols=jar
