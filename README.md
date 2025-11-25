# wildfly-graal

# WildFly core

# Build dependencies

* clone and build: https://github.com/jfdenise/wildfly-core/pull/new/graal-poc
* galleon install wildfly-core#31.0.0.Beta3-SNAPSHOT --layers=deployment-scanner,io,logging --dir=min-server2
* clone and build:  https://github.com/jfdenise/jboss-modules/pull/new/2.x-graal-poc
* cd module-launcher
* mvn clean install



## Prepare the built image

* Run : JAVA_OPTS="-agentlib:native-image-agent=config-output-dir=./min-server-graal-agent2,experimental-class-define-support=true" sh ./min-server2/bin/standalone.sh
* Kill the server

## Build the image

* Add to min-server-graal-agent2/reachability-metadata.json (in reflection):

    {
    "type": "org.jboss.logmanager.ExtHandler",
    "methods": [
      {
        "name": "setEnabled",
        "parameterTypes": [
          "boolean"
        ]
      }
    ]
  },
** Specific for elytron

*** Add to min-server-graal-agent2/reachability-metadata.json (in reflection):
  {
    "type": "org.wildfly.security.WildFlyElytronDigestProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.WildFlyElytronHttpBasicProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.WildFlyElytronHttpBearerProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.WildFlyElytronHttpClientCertProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.WildFlyElytronHttpDigestProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.WildFlyElytronHttpFormProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.WildFlyElytronHttpSpnegoProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.WildFlyElytronProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.key.WildFlyElytronKeyProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.password.WildFlyElytronPasswordProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.credential.store.WildFlyElytronCredentialStoreProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.sasl.localuser.WildFlyElytronSaslLocalUserProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.sasl.anonymous.WildFlyElytronSaslAnonymousProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.sasl.gssapi.WildFlyElytronSaslGssapiProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.sasl.external.WildFlyElytronSaslExternalProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.http.external.WildFlyElytronHttpExternalProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.http.bearer.WildFlyElytronHttpBearerProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.sasl.digest.WildFlyElytronSaslDigestProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.sasl.entity.WildFlyElytronSaslEntityProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.sasl.gs2.WildFlyElytronSaslGs2Provider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.http.cert.WildFlyElytronHttpClientCertProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.sasl.scram.WildFlyElytronSaslScramProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.http.digest.WildFlyElytronHttpDigestProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.sasl.plain.WildFlyElytronSaslPlainProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.sasl.oauth2.WildFlyElytronSaslOAuth2Provider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.sasl.otp.WildFlyElytronSaslOTPProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.http.spnego.WildFlyElytronHttpSpnegoProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
  {
    "type": "org.wildfly.security.keystore.WildFlyElytronKeyStoreProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.digest.WildFlyElytronDigestProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.http.form.WildFlyElytronHttpFormProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.auth.client.WildFlyElytronClientDefaultSSLContextProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.security.http.basic.WildFlyElytronHttpBasicProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.wildfly.openssl.OpenSSLProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.bouncycastle.jce.provider.BouncyCastleProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  },
    {
    "type": "org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  }

*** Add to min-server-graal-agent2/reachability-metadata.json (in resources):

    {
      "glob": "org/wildfly/security/ssl/TLS13MechanismDatabase.properties"
    },
    {
      "glob": "org/wildfly/security/ssl/MechanismDatabase.properties"
    },

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
