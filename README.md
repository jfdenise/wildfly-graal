# wildfly-graal

* Nothing in the classpath
* Load what needs to be loaded at build time (in a pre-main)
* Then run the server in main.

# Install latest graalvm (JDK25)

* Download from https://www.oracle.com/downloads/graalvm-downloads.html
* Then call in the terminal:

```
export GRAALVM_HOME=<path to graal>/Contents/Home/
export JAVA_HOME=${GRAALVM_HOME}
export PATH=${GRAALVM_HOME}/bin:$PATH
```

Test that native-image is OK, call `native-image --help`

# Build WildFly and dependencies

WARNING YOU MUST USE JDK21.

```
git clone -b archive_servlet_starting git@github.com:jfdenise/wildfly-graal
git clone -b archive_servlet_starting git@github.com:jfdenise/jboss-modules
git clone -b archive_servlet_starting git@github.com:jfdenise/jboss-vfs
git clone -b archive_servlet_starting git@github.com:jfdenise/jboss-msc
git clone -b archive_servlet_starting git@github.com:jfdenise/xnio
git clone -b archive_servlet_starting git@github.com:jfdenise/undertow

cd jboss-modules; mvn clean install -DskipTests; cd ..
cd jboss-vfs; mvn clean install -DskipTests; cd ..
cd jboss-msc; mvn clean install -DskipTests; cd ..
cd xnio; mvn clean install -DskipTests; cd ..
cd undertow; mvn clean install -DskipTests; cd ..

git clone -b archive_servlet_starting git@github.com:jfdenise/wildfly-core
git clone -b archive_servlet_starting git@github.com:jfdenise/wildfly

cd wildfly-core; mvn clean install -DskipTests; cd ..
cd wildfly; mvn clean install -DskipTests; cd ..

cd wildfly-graal
cd module-launcher; mvn clean install -DskipTests; cd ..
cd agent; mvn clean install -DskipTests; cd ..
cd wildfly-substitutions;mvn clean install -DskipTests;cd ..
'''

# Provision a WildFly server

* download Galleon from https://github.com/wildfly/galleon/releases/download/6.1.1.Final/galleon-6.1.1.Final.zip, 
unzip it and call: `galleon-6.1.1.Final/bin/galleon.sh install wildfly#39.0.0.Beta1-SNAPSHOT --layers=base-server,io,elytron,servlet --dir=min-core-server`

NOTE: make sure to provision the server in the wildfly-graal repo root directory.

# Copy the files needed by the demo

We do:

* Disable the PeriodicFile logger (incompatible with build time initialization).
* Copy the welcome content
* Cleanup previous session of services recording

```
cp files/logging.properties min-core-server/standalone/configuration
cp -r files/welcome-content min-core-server/
rm -rf jboss-modules-recorded-services/
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

## Run the agent to dump service loaders

* This step will be removed possibly in a next phase.

```
JAVA_OPTS="-javaagent:agent/target/wildfly-graal-agent.jar" sh ./min-core-server/bin/standalone.sh
```

## Build the image

* Call: `sh ./build-wildfly-image.sh`

## Run the image

* `./wildfly-launcher`
* Access the page: http://127.0.0.1:8080
