# wildfly-graal

* Nothing in the classpath
* Load what needs to be loaded at build time (in a pre-main)
* Then run the server in main.

* For now, focusing on WildFly core

# Install graalvm

* Download from https://www.oracle.com/downloads/graalvm-downloads.html
* Then call in the terminal:

```
export GRAALVM_HOME=<path to graal>/Contents/Home/
export JAVA_HOME=${GRAALVM_HOME}
export PATH=${GRAALVM_HOME}/bin:$PATH
```

Test that native-image is OK, call `native-image --help`

# Build dependencies

* clone this branch : https://github.com/jfdenise/wildfly-graal/tree/remove_content_from_classpath
* cd wildfly-graal
* clone JBoss Modules:  https://github.com/jfdenise/jboss-modules/tree/2.x-graal-poc-remove_content_from_classpath
* call: `cd jboss-modules; mvn clean install -DskipTests; cd ..`
* clone JBoss VFS: https://github.com/jfdenise/jboss-vfs/tree/graal-poc-remove_content_from_classpath
* call: `cd jboss-vfs; mvn clean install -DskipTests; cd ..`
* clone XNIO: https://github.com/jfdenise/xnio/tree/3.8-graal-poc-remove_content_from_classpath
* call: `cd xnio; mvn clean install -DskipTests; cd ..`
* call: `cd module-launcher; mvn clean install; cd ..`

# Provision a WildFly Core server

* clone and build: https://github.com/jfdenise/wildfly-core/tree/graal-poc-empty-classpath
* download Galleon from https://github.com/wildfly/galleon/releases/download/6.1.1.Final/galleon-6.1.1.Final.zip, 
unzip it and call: `galleon-6.1.1.Final/bin/galleon.sh install wildfly-core#31.0.0.Beta3-SNAPSHOT --layers=base-server,io --dir=min-core-server`

NOTE: make sure to provision the server in the wildfly-graal repo root directory.

# Copy the files needed by the demo

We do:

* Disable the PeriodicFile logger (incompatible with build time initialization).

```
cp files/logging.properties min-core-server/standalone/configuration 
```

## Build the image

* Call: `sh ./build-wildfly-image.sh`

## Run the image

* `./ModuleLauncher-1.0-SNAPSHOT`
