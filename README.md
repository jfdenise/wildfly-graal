# wildfly-graal

* Build hello and module-launcher
* cp hello/target/HelloModule-1.0-SNAPSHOT.jar modules/hello/main/hello.jar

Run it with java:

* java  -cp /Users/jdenise/.m2/repository/org/jboss/modules/jboss-modules/2.2.0.Final/jboss-modules-2.2.0.Final.jar:module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar launcher.Launcher

## Build native image (JPMS)

native-image --module-path /Users/jdenise/.m2/repository/org/jboss/modules/jboss-modules/2.2.0.Final/jboss-modules-2.2.0.Final.jar:module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar:hello/target/HelloModule-1.0-SNAPSHOT.jar --module LauncherModule  --add-modules HelloModule --initialize-at-build-time launcher.Launcher


## Build native image (JPMS + CP)

native-image --module-path /Users/jdenise/.m2/repository/org/jboss/modules/jboss-modules/2.2.0.Final/jboss-modules-2.2.0.Final.jar:module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp hello/target/HelloModule-1.0-SNAPSHOT.jar --module LauncherModule --initialize-at-build-time launcher.Launcher

## Build native image (JAR + CP)

native-image -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar -cp /Users/jdenise/.m2/repository/org/jboss/modules/jboss-modules/2.2.0.Final/jboss-modules-2.2.0.Final.jar:hello/target/HelloModule-1.0-SNAPSHOT.jar --initialize-at-build-time launcher.Launcher


## Run the image

./launcher.Launcher 
