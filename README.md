# wildfly-graal

* Nothing in the classpath
* Start the server suspended at build time. Capture what we can.
* Then run the server at runtime.

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

WARNING YOU MUST USE JDK17.

```
git clone -b wildfly_graal_elytron_services git@github.com:jfdenise/wildfly-graal
git clone -b wildfly_graal_elytron_services git@github.com:jfdenise/jboss-modules
git clone -b archive_servlet_starting git@github.com:jfdenise/jboss-vfs
git clone -b archive_servlet_starting git@github.com:jfdenise/jboss-msc
git clone -b archive_servlet_starting git@github.com:jfdenise/xnio
git clone -b websocket_continuing git@github.com:jfdenise/undertow
git clone -b wildfly_graal_elytron_services git@github.com:jfdenise/wildfly-elytron
git clone -b wildfly_graal_elytron_services git@github.com:jfdenise/jboss-remoting

cd jboss-modules; mvn clean install -DskipTests; cd ..
cd jboss-vfs; mvn clean install -DskipTests; cd ..
cd jboss-msc; mvn clean install -DskipTests; cd ..
cd xnio; mvn clean install -DskipTests; cd ..
cd undertow; mvn clean install -DskipTests; cd ..
cd wildfly-elytron; mvn clean install -DskipTests; cd ..
cd jboss-remoting; mvn clean install -DskipTests; cd ..

git clone -b wildfly_graal_elytron_services git@github.com:jfdenise/wildfly-core
git clone -b websocket_continuing git@github.com:jfdenise/wildfly

cd wildfly-core; mvn clean install -DskipTests; cd ..
cd wildfly; mvn clean install -DskipTests; cd ..

cd wildfly-graal
cd module-launcher; mvn clean install -DskipTests; cd ..
cd agent; mvn clean install -DskipTests; cd ..
cd wildfly-substitutions;mvn clean install -DskipTests;cd ..

```

# Provision a WildFly server

* download Galleon from https://github.com/wildfly/galleon/releases/download/6.1.1.Final/galleon-6.1.1.Final.zip, 
unzip it and call: `galleon-6.1.1.Final/bin/galleon.sh install wildfly#39.0.0.Beta1-SNAPSHOT --layers=base-server,io,elytron,servlet,core-tools --dir=min-core-server`

NOTE: make sure to provision the server in the wildfly-graal repo root directory.

# Copy the files needed by the demo

We do:

* Disable the PeriodicFile logger (incompatible with build time initialization).
* Copy the welcome content
* Cleanup previous session of services recording

```
cp files/logging.properties min-core-server/standalone/configuration
cp -r files/welcome-content min-core-server/
```

# Create the authenticated user

```
min-core-server/bin/add-user.sh -a -u 'quickstartUser' -p 'quickstartPwd1!' -g Users
```

# Deploy the deployment


## Build and explode the deployment

```
cd deployment-src/helloworld;mvn clean install;cd ../..
rm -rf min-core-server/deployment-exploded
unzip deployment-src/helloworld/target/helloworld.war -d min-core-server/deployment-exploded
```

## Pre-compile the jsp and install it in the exploded deployment

```
git clone https://github.com/rmartinc/jspc
cd jspc; mvn clean install -DskipTests; cd ..
cd jspc/tool
mkdir -p precompiled/classes/META-INF
mvn exec:java -Dexec.args="-v -p pre.compiled.jsps -d  precompiled/classes -webapp ../../min-core-server/deployment-exploded -webfrg  precompiled/classes/META-INF/web-fragment.xml"
cd precompiled/classes
jar cvf precompiled-jsp.jar *
mkdir -p ../../../../min-core-server/deployment-exploded/WEB-INF/lib
cp precompiled-jsp.jar ../../../../min-core-server/deployment-exploded/WEB-INF/lib
cd ../../../..
```

## Build the custom auth module

```
cd deployment-src/custom-module;mvn clean install;cd ../..
```

Add to standalone.xml:

```
    <deployments>
        <deployment name="helloworld.war" runtime-name="helloworld.war">
            <fs-exploded path="deployment-exploded" relative-to="jboss.home.dir"/>
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

## Use WildFly CLI to update the configuration and deploy the custome auth module

```
sh ./min-core-server/bin/standalone.sh &
cd deployment-src
../min-core-server/bin/jboss-cli.sh --file=add-custom-module.cli
../min-core-server/bin/jboss-cli.sh -c --file=configure-elytron.cli
cd ..
```

Kill the server.

## Run the agent to dump service loaders

* This step will be removed possibly in a next phase.

```
rm -rf jboss-modules-recorded-services
JAVA_OPTS="-javaagent:agent/target/wildfly-graal-agent.jar" sh ./min-core-server/bin/standalone.sh
```
## Build the image

* Call: `sh ./build-wildfly-image.sh`

## Run the image

* `./wildfly-launcher`
* Access the page: http://127.0.0.1:8080/helloworld/HelloWorld
* Access the pre-compiled JSP: http://127.0.0.1:8080/helloworld/simple.jsp
* Access the websocket 1: http://127.0.0.1:8080/helloworld/websocket.html
* Access the websocket 2 (with encoding/decoding): http://127.0.0.1:8080/helloworld/bid.html
* Access the secured servlet: `curl -v http://localhost:8080/helloworld/secured -H "X-USERNAME:quickstartUser" -H "X-PASSWORD:password"`
