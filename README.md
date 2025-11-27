# wildfly-graal Demo

This demo is an initial approach to have WildFly to benefit from Graal VM with minimal changes to server and dependencies.

* Currently, the idea is to use JBoss Modules (that is core in WildFly) and see what we can do.
* Server JBoss Modules are initialized at build time.
* All jars are put in the classpath.
* We benefit from the `experimental-class-define-support` support of Graal VM to define the WAR classes.

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

* clone this branch : https://github.com/jfdenise/wildfly-graal/tree/build_time_add_deployment
* cd wildfly-graal
* clone JBoss Modules:  https://github.com/jfdenise/jboss-modules/pull/new/2.x-graal-poc
* call: `cd jboss-modules; mvn clean install; cd ..`
* call: `cd module-launcher; mvn clean install; cd ..`

# Provision a WildFly server

* clone and build: https://github.com/jfdenise/wildfly-core/pull/new/graal-poc
* clone and build https://github.com/jfdenise/wildfly/pull/new/graal-poc
* download Galleon from https://github.com/wildfly/galleon/releases/download/6.1.1.Final/galleon-6.1.1.Final.zip, 
unzip it and call: `galleon-6.1.1.Final/bin/galleon.sh install wildfly#39.0.0.Beta1-SNAPSHOT --layers=ee-core-profile-server,jaxrs --dir=min-server2`

NOTE: make sure to provision the server in the wildfly-graal repo root directory.

# Copy the deployment to WildFly

```
cp deployments/helloworld-rs.war min-server2/standalone/deployments
```

# Copy the files needed by the demo

We do:

* Disable the PeriodicFile logger (incompatible with build time initialization).

```
cp files/standalone.xml min-server2/standalone/configuration 
cp files/logging.properties min-server2/standalone/configuration 
cp -r files/welcome-content min-server2/
```

# Run the server and access the application

In this phase we capture the relective access and defined classes. In particular the WAR classes.

```
JAVA_OPTS="-agentlib:native-image-agent=config-output-dir=./min-server-graal-agent2,experimental-class-define-support=true" sh ./min-server2/bin/standalone.sh
```
* Access the servlet http://127.0.0.1:8080/helloworld-rs
* Kill the server

## Build the image

* Call: `sh ./build-wildfly-image.sh`

## Run the image

* `./ModuleLauncher-1.0-SNAPSHOT`

* Access the servlet http://127.0.0.1:8080/helloworld-rs