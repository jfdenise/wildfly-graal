# wildfly-graal Demo

This demo is an initial approach to have WildFly to benefit from Graal VM with minimal changes to server and dependencies.

* Currently, the idea is to use JBoss Modules (that is core in WildFly) and see what we can do.
* For now, the JBoss Modules are loaded at runtime, more investigation is planned to load them at build time to have better perf.
* All jars are put in the classpath.
* logmanager is initialized at buildtime
* A bunch of types are loaded through reflection, we use a reachability-metadata.json that contains the needed types and resources.

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

* clone and build JBoss Modules:  https://github.com/jfdenise/jboss-modules/pull/new/2.x-graal-poc
* clone this branch : https://github.com/jfdenise/wildfly-graal/tree/wildfly_welcome_demo
* cd wildfly-graal
* call: `cd module-launcher; mvn clean install; cd ..`

# Provision a WildFly server

* clone and build: https://github.com/jfdenise/wildfly-core/pull/new/graal-poc
* clone and build https://github.com/jfdenise/wildfly/pull/new/graal-poc
* download Galleon from https://github.com/wildfly/galleon/releases/download/6.1.1.Final/galleon-6.1.1.Final.zip, 
unzip it and call: `galleon-6.1.1.Final/bin/galleon.sh install wildfly#39.0.0.Beta1-SNAPSHOT --layers=core-server,servlet --dir=min-server2`

NOTE: make sure to provision the server in the wildfly-graal repo root directory.

# Copy the files needed by the demo

```
mkdir -p min-server-graal-agent2
cp files/reachability-metadata.json min-server-graal-agent2/
cp files/standalone.xml min-server2/standalone/configuration 
cp files/logging.properties min-server2/standalone/configuration 
cp files/mgmt-users.properties min-server2/standalone/configuration 
cp -r files/welcome-content min-server2/
cp deployments/helloworld.war min-server2/standalone/deployments
```

## Build the image

* Call: `sh ./build-wildfly-image.sh`

## Run the image

* `./ModuleLauncher-1.0-SNAPSHOT`

* Access (with user admin, password admin) to http://127.0.0.1:9990/management 
* Access the page http://127.0.0.1:8080
* Acess the servlet http://127.0.0.1:8080/helloworld