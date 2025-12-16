# wildfly-graal

* Nothing in the classpath
* Load what needs to be loaded at build time (in a pre-main)
* Then run the server in main.

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
* clone JBoss Modules:  https://github.com/jfdenise/jboss-modules/tree/2.x-graal-poc-remove_content_from_classpath_cleanup
* call: `cd jboss-modules; mvn clean install -DskipTests; cd ..`
* clone JBoss VFS: https://github.com/jfdenise/jboss-vfs/tree/graal-poc-remove_content_from_classpath
* call: `cd jboss-vfs; mvn clean install -DskipTests; cd ..`
* clone XNIO: https://github.com/jfdenise/xnio/tree/3.8-graal-poc-remove_content_from_classpath_cleanup
* call: `cd xnio; mvn clean install -DskipTests; cd ..`
* clone undertow: https://github.com/jfdenise/undertow/tree/graal-poc-empty-classpath
* call: `cd undertow; mvn clean install -DskipTests; cd ..`
* call: `cd module-launcher; mvn clean install; cd ..`

# Build the tooling

* The monitor of loaded classes and services loaders, call: `cd agent; mvn clean install; cd ..`
* The Graal VM substituions (executed at runtime, call: `cd wildfly-substitutions;mvn clean install;cd ..`

# Provision a WildFly server

 cal: `rm -rf min-core-server`
* clone and build: https://github.com/jfdenise/wildfly-core/tree/graal-poc-empty-classpath_add-deployment_cleanup
* clone and build: https://github.com/jfdenise/wildfly/tree/graal-poc-empty-classpath_add_deployment_cleanup
* download Galleon from https://github.com/wildfly/galleon/releases/download/6.1.1.Final/galleon-6.1.1.Final.zip, 
unzip it and call: `galleon-6.1.1.Final/bin/galleon.sh install wildfly#39.0.0.Beta1-SNAPSHOT --layers=base-server,io,elytron,servlet --dir=min-core-server`

NOTE: make sure to provision the server in the wildfly-graal repo root directory.

# Copy the files needed by the demo

We do:

* Disable the PeriodicFile logger (incompatible with build time initialization).

```
cp files/logging.properties min-core-server/standalone/configuration
cp -r files/welcome-content min-core-server/
rm -rf reflective-dump jboss-modules-recorded-classes jboss-modules-recorded-services/
mkdir -p reflective-dump
cp files/reachability-metadata.json reflective-dump
```

* Add the modularized deployment

```
cp -r deployment min-core-server/modules/system/layers/base
```

# Deploy the deployment

That is a hack for now to have an event at boot to deploy something....
Add to standalone.xml:

```
    <deployments>
        <deployment name="helloworld.war" runtime-name="helloworld.war">
            <fs-exploded path="/Users/jdenise/workspaces/graal/wildfly-graal/deployment/helloworld/war/main/" />
        </deployment>
    </deployments>
```

## Remove content from the server config

* Elytron:

```
<!--<audit-logging>
    <file-audit-log name="local-audit" path="audit.log" relative-to="jboss.server.log.dir" format="JSON"/>
</audit-logging>-->
```

## Add the welcome content to the server config

Replace undertow subsystem with:

```
<subsystem xmlns="urn:jboss:domain:undertow:community:14.0" default-virtual-host="default-host" default-servlet-container="default" default-server="default-server" statistics-enabled="${wildfly.undertow.statistics-enabled:${wildfly.statistics-enabled:false}}">
    <byte-buffer-pool name="default"/>
    <buffer-cache name="default"/>
    <server name="default-server">
        <http-listener name="default" socket-binding="http" redirect-socket="https" enable-http2="true"/>
        <host name="default-host" alias="localhost">
            <location name="/" handler="welcome-content"/>
            <http-invoker/>
        </host>
    </server>
    <servlet-container name="default">
        <jsp-config/>
        <websockets/>
    </servlet-container>
    <handlers>
      <file name="welcome-content" path="${jboss.home.dir}/welcome-content"/>
    </handlers>
</subsystem>
```

## Run the agent to dump classes and service loaders

JAVA_OPTS="-agentlib:native-image-agent=config-output-dir=./reflective-dump -javaagent:agent/target/wildfly-graal-agent.jar" sh ./min-core-server/bin/standalone.sh

## Build the image

* Call: `sh ./build-wildfly-image.sh`

## Run the image

* `./wildfly-launcher`
* Access the page: http://127.0.0.1:8080

## ISSUES

* We can't substitute Constructor on the fly, Substrat VM already substitute java.lang.Class...
So it means that we need for instances constructed with reflection (a lot in JSP, Servlet, ...) to be put in the classpath and rely on 
dynamic feature (reflection.json file) to handle them. Problem: Too much goes into the classpath, all undertow, logging, ...

To solve the issue:
* Refactor the code to rely on pre-loaded constructors
* Follow the future dynamic feature for custom classloader to be added to Graal.

## What we can already do

* Handle ServiceLoading fully (subsitution)
* Handle class loading (substitution of ModuleClassLoader find class) to retrieve from the cache.

## The approach

* Run the java server to capture:
** Loaded services
** Loaded classes
** Reflective metadata to retrieve constructors

* Build the image
** At build time
*** Load all jboss modules
*** Populate the classes, constuctors and services caches
** Initialize a bunch of server classes (--initialize-at-build-time=)

## What do we have in the classpath

This implies the `com.sun.el.ExpressionFactoryImpl` constructor to be in the `reachability-metadata.json` file provided at build time.
* org/glassfish/expressly and jakarta/el/api in classpath (doesn't require more than that). To avoid changes in jakarta.el spec.


