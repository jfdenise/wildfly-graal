# wildfly-graal

* Start the server suspended at build time.
* Passivate the services, cleanup what need to be cleanup.
* At runtime, activate the services.

# Current observed numbers:

* Startup time 10 to 15ms vs 2secs in Java
* Memory (RSS): 8MB vs 28MB in Java.

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
git clone -b cleanup_2026_04_21 git@github.com:jfdenise/wildfly-graal
git clone -b cleanup_2026_04_21 git@github.com:jfdenise/jboss-modules
git clone -b cleanup_2026_04_21 git@github.com:jfdenise/jboss-vfs
git clone -b cleanup_2026_04_21 git@github.com:jfdenise/jboss-msc
git clone -b cleanup_2026_04_21 git@github.com:jfdenise/xnio
git clone -b max_server_init_at_build_time_2026_03_12 git@github.com:jfdenise/undertow
git clone -b wildfly_graal_runtime git@github.com:jfdenise/wildfly-elytron
git clone -b cleanup_2026_04_21 git@github.com:jfdenise/jboss-remoting
git clone -b wildfly_graal_2026_03_05 git@github.com:jfdenise/resteasy
git clone -b max_server_init_at_build_time_2026_04_01 git@github.com:jfdenise/jboss-jakarta-el-api_spec

cd wildfly-graal/runtime;mvn clean install;cd ../..

cd jboss-modules; mvn clean install -DskipTests; cd ..
cd jboss-vfs; mvn clean install -DskipTests; cd ..
cd jboss-msc; mvn clean install -DskipTests; cd ..
cd xnio; mvn clean install -DskipTests; cd ..
cd undertow; mvn clean install -DskipTests; cd ..
cd wildfly-elytron; mvn clean install -DskipTests -DskipCompatibility=true ; cd ..
cd jboss-remoting; mvn clean install -DskipTests; cd ..
cd resteasy; mvn clean install -DskipTests; cd ..
cd jboss-jakarta-el-api_spec; mvn clean install -DskipTests; cd ..

git clone -b max_server_init_at_build_time_2026_03_12 git@github.com:jfdenise/wildfly-core
git clone -b max_server_init_at_build_time_2026_03_12 git@github.com:jfdenise/wildfly

cd wildfly-core; mvn clean install -DskipTests; cd ..
cd wildfly; mvn clean install -DskipTests; cd ..

cd wildfly-graal
cd module-launcher; mvn clean install -DskipTests; cd ..
cd agent; mvn clean install -DskipTests; cd ..
cd wildfly-substitutions;mvn clean install -DskipTests;cd ..
cd analyzer;mvn clean install;cd ..

```

# How to build a compiled wildFly

* In one step (if you don't seed to twak the server prior compilation): `sh ./build-wildfly-image.sh <path to a war file>`

* In two steps: `sh ./provision-wildfly-server.sh <path to a war file>;sh ./build-wildfly-image.sh`

# Demos

## Build and explode the deployment

```
cd deployment-src/helloworld;mvn clean install;cd ../..
rm -rf tmp
mkdir -p tmp
unzip deployment-src/helloworld/target/helloworld.war -d tmp/deployment-exploded
```

## Pre-compile the jsp, install it in the exploded deployment and rezip

```
git clone https://github.com/rmartinc/jspc
cd jspc; mvn clean install -DskipTests; cd ..
cd jspc/tool
mkdir -p precompiled/classes/META-INF
mvn exec:java -Dexec.args="-v -p pre.compiled.jsps -d  precompiled/classes -webapp ../../tmp/deployment-exploded -webfrg  precompiled/classes/META-INF/web-fragment.xml"
cd precompiled/classes
jar cvf precompiled-jsp.jar *
mkdir -p ../../../../tmp/deployment-exploded/WEB-INF/lib
cp precompiled-jsp.jar ../../../../tmp/deployment-exploded/WEB-INF/lib
cd ../../../..
cd tmp/deployment-exploded
zip ../ROOT.war * */**/*
cd ../..
```

# Provision a WildFly server and deploy the deployment.

```
sh ./provision-wildfly-server.sh tmp/ROOT.war
```

# The demo

## Create the authenticated user

```
analyzer-output/wildfly-server/bin/add-user.sh -a -u 'quickstartUser' -p 'quickstartPwd1' -g Users
```

## Build the custom auth module

```
cd deployment-src/custom-module;mvn clean install;cd ../..
```


## Use WildFly CLI to update the configuration and deploy the custom auth module

```
sh ./analyzer-output/wildfly-server/bin/standalone.sh &
cd deployment-src
../analyzer-output/wildfly-server/bin/jboss-cli.sh --file=add-custom-module.cli
../analyzer-output/wildfly-server/bin/jboss-cli.sh -c --file=configure-elytron.cli
cd ..
```
Kill the server.

# Build the image

* Call: `sh ./build-wildfly-image.sh`

# Run the image

* `./wildfly-launcher`
* Access the page: http://127.0.0.1:8080/HelloWorld
* Access the pre-compiled JSP: http://127.0.0.1:8080/simple.jsp
* Servlet filter: http://127.0.0.1:8080/FilterExample
* Access the websocket 1: http://127.0.0.1:8080/websocket.html
* Access the websocket 2 (with encoding/decoding): http://127.0.0.1:8080/bid.html
* Access the secured servlet: `curl -v http://localhost:8080/secured -H "X-USERNAME:quickstartUser" -H "X-PASSWORD:password"`
* Access the REST1: http://127.0.0.1:8080/rest/HelloWorld?from=100&to=200&orderBy=age&orderBy=FOO
* Access the REST2: http://127.0.0.1:8080/rest2/HelloWorld2?from=100&to=200&orderBy=age&orderBy=name
* Access REST + JSON Bindings: http://localhost:8080/rest3/library/rectangle
* Access REST + JSON Bindings: http://localhost:8080/rest3/library/books
* Access REST + JSON Bindings: http://localhost:8080/rest3/library/books/9780596529260
* Access REST + JSON Bindings (file upload): http://localhost:8080/upload.html
* Access REST + JSAPI: http://localhost:8080/jsapi.html
* Access REST + RestEasy Tracing extension: http://localhost:8080/tracing.html
* Connect the WildFly CLI: `./analyzer-output/wildfly-server/bin/jboss-cli.sh -c`
```
/subsystem=logging/console-handler=CONSOLE:write-attribute(name=level,value=ALL)
/subsystem=logging/logger=org.wildfly.graal:add(level=ALL)
```
NOTE: Exit the CLI, then try to reconnect, will fail 80% of the time. We have a race condition in XNIO I suppose.

Then access again to http://127.0.0.1:8080/bid.html You will see traces in the console.

# CDI + EE security demo

## Build the deployment and provision a new server

* `cd deployment-src/ee-security;mvn clean install;cd ../..`
* `sh ./provision-wildfly-server.sh deployment-src/ee-security/target/ee-security.war`

## Create the authenticated user

```
analyzer-output/wildfly-server/bin/add-user.sh -a -u 'quickstartUser' -p 'quickstartPwd1' -g Users
```

## Build the image

* Call: `sh ./build-wildfly-image.sh`

## Start the server

* Call: `./wildfly-launcher`

## Access the servlet

* `curl -v http://localhost:8080/secured-cdi -H "X-USERNAME:quickstartUser" -H "X-PASSWORD:quickstartPwd1"`

# More resteasy examples that have been tested

The repo is: https://github.com/resteasy/resteasy-examples

* Build the example then

* Call: `sh ./build-wildfly-image.sh <path to the example war file>`
* Call: `./wildfly-launcher`

* Then activate the deployment the way it is documented in the next chapters.

## Resteasy async-job-service

https://github.com/resteasy/resteasy-examples/tree/main/async-job-service

curl --verbose --request POST --header "Content-Type:  text/plain" --data "my message" http://localhost:8080/resource/
curl --verbose http://localhost:8080/resource/


## CDI + bean validation

FAILURE, bean-validation requires some reflection that we failed to move at build time due to generated CDI proxy being themselves introspected. And we
don't want to do that at build time. The example used for the attempt: https://github.com/wildfly/quickstart/tree/main/jaxrs-jwt


# Some notes

* If we don't specify the packages to load at build time, _logger are not found at runtime. So we need to build the list of all packages to put in the script.

* CREMA will be used to fix the reflection issues we have at runtime. We can't use the Graal VM support for reflection that only works for classes in the classpath.

* CDI: We collect all the classes that could be injected and JAXRS endpoints + some well known and we force generate proxies at build time. It seems to work, at least for the xamples
tried. 

* Stopping at the Bean validation level. We have added in the CdiValidatorFactoryService, a way to force creation of the cache needed for validation 
for cdi classes but that has not been enough, The Proxy classes can also be validated and require to be cached. We will not do that. 
The dependency is hibernate-validator, in particular the class ValidatorImpl and BeanMetaDataImpl


# TODO

* Cleanup of permissions handling, we don't care about permissions, no more security manager
* Cleanup of some useless pre loading of services now that the server is started once in the heap.
