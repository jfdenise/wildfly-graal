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

WARNING: Do NOT use graal VM to build the dependencies, use JDK21 when building the dependencies.

* clone this branch : https://github.com/jfdenise/wildfly-graal/tree/archive-io-elytron-undertow-starting
* cd wildfly-graal
* clone JBoss Modules:  https://github.com/jfdenise/jboss-modules/tree/2.x-graal-poc-remove_content_from_classpath
* call: `cd jboss-modules; mvn clean install -DskipTests; cd ..`
* clone JBoss VFS: https://github.com/jfdenise/jboss-vfs/tree/graal-poc-remove_content_from_classpath
* call: `cd jboss-vfs; mvn clean install -DskipTests; cd ..`
* clone XNIO: https://github.com/jfdenise/xnio/tree/3.8-graal-poc-remove_content_from_classpath
* call: `cd xnio; mvn clean install -DskipTests; cd ..`
* clone undertow: https://github.com/jfdenise/undertow/tree/graal-poc-empty-classpath
* call: `cd undertow; mvn clean install -DskipTests; cd ..`
* call: `cd module-launcher; mvn clean install; cd ..`

# Provision a WildFly server

WARNING: Do NOT use graal VM to build the server, use JDK21 when building wildfly-core and wildfly.

* clone and build: https://github.com/jfdenise/wildfly-core/tree/graal-poc-empty-classpath
* clone and build: https://github.com/jfdenise/wildfly/tree/graal-poc-empty-classpath
* download Galleon from https://github.com/wildfly/galleon/releases/download/6.1.1.Final/galleon-6.1.1.Final.zip, 
unzip it and call: `galleon-6.1.1.Final/bin/galleon.sh install wildfly#39.0.0.Beta1-SNAPSHOT --layers=base-server,io,elytron,undertow --dir=min-core-server`

NOTE: make sure to provision the server in the wildfly-graal repo root directory.

# Copy the files needed by the demo

We do:

* Disable the PeriodicFile logger (incompatible with build time initialization).

```
cp files/logging.properties min-core-server/standalone/configuration
cp -r files/welcome-content min-core-server/
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
        </host>
    </server>
    <servlet-container name="default"/>
     <handlers>
        <file name="welcome-content" path="${jboss.home.dir}/welcome-content"/>
    </handlers>
</subsystem>
```

## Build the image

* Call: `sh ./build-wildfly-image.sh`

## Run the image

* `./wildfly-launcher`
* Access the page: http://127.0.0.1:8080
