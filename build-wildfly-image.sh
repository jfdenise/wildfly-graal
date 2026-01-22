current_dir=$(pwd)
JBOSS_HOME=${current_dir}/min-core-server

IFS=$'\n'
array=($(find ${JBOSS_HOME}/modules/system/layers/base/ -name \*.jar))
unset IFS

arraylength=${#array[@]}


echo "Analyzing the server and deployment"
java -jar analyzer/target/Analyzer-1.0-SNAPSHOT.jar ${JBOSS_HOME} ${JBOSS_HOME}/helloworld.war

cmd="
native-image -jar module-launcher/target/wildfly-graal-launcher-1.0-SNAPSHOT.jar \\
wildfly-launcher \\
-Dorg.wildfly.graal.deployment.module=deployment.helloworld.war \\
-Djboss.home.dir=${JBOSS_HOME} \\
-Djava.util.logging.manager=org.jboss.logmanager.LogManager \\
-Djboss.modules.system.pkgs=org.jboss.modules,org.wildfly.graal,org.jboss.logmanager,org.jboss.logging \\
-Dlogging.configuration=file:${JBOSS_HOME}/standalone/configuration/logging.properties \\
-H:+PrintClassInitialization \\
--trace-object-instantiation=java.lang.ref.Cleaner \\
--initialize-at-build-time=\\"

# Classes hard coded, not discovered but needed
cmd="$cmd
launcher,\\
java.beans,\\
java.awt.color,\\
sun.java2d.cmm,\\
org.xml.sax,\\
sun.security.jgss.GSSManagerImpl"

# All server discovered classes
while read -r line; do
    name="$line"
    cmd="$cmd,\\
$name"
done < "allServerPackages.txt"
cmd="$cmd \\"

# All classes that can't be init at build time

cmd="$cmd
--initialize-at-run-time=\\
io.smallrye.common.os.Process,\\
io.smallrye.common.net.CidrAddress,\\
io.smallrye.common.net.Inet,\\
io.undertow.security.impl.SimpleNonceManager,\\
io.undertow.server.handlers.resource.DirectoryUtils\\\$Blobs,\\
io.undertow.server.protocol.ajp.AjpServerResponseConduit,\\
io.undertow.server.protocol.ajp.AjpServerRequestConduit,\\
org.eclipse.jgit.util.FileUtils,\\
org.eclipse.jgit.transport.HttpAuthMethod\\\$Digest,\\
org.eclipse.jgit.internal.storage.file.WindowCache,\\
org.eclipse.jgit.lib.RepositoryCache,\\
org.eclipse.jgit.lib.internal.WorkQueue,\\
org.jboss.as.domain.http.server.ManagementHttpServer,\\
org.jboss.as.server.DomainServerCommunicationServices,\\
org.jboss.as.server.deployment.module.TempFileProviderService,\\
org.jboss.as.server.operations.NativeManagementServices,\\
org.jboss.as.server.services.net.BindingAddHandler,\\
org.jboss.as.server.services.net.SocketBindingResourceDefinition,\\
org.jboss.classfilewriter.DefaultClassFactory,\\
org.jboss.msc.service.ServiceContainer\\\$Factory,\\
org.jboss.remoting3.ConfigurationEndpointSupplier\\\$Holder,\\
org.jboss.remoting3.ConnectionInfo,\\
org.jboss.remoting3.remote.RemoteConnection,\\
org.jboss.remoting3.remote.MessageReader,\\
org.jboss.resteasy.spi.ResourceCleaner,\\
org.bouncycastle.mail.smime.SMIMESignedGenerator,\\
org.wildfly.common.net,\\
org.wildfly.httpclient.common.ConfigurationHttpContextSupplier,\\
org.wildfly.httpclient.common.HttpContextGetterHolder,\\
org.wildfly.httpclient.common.PoolAuthenticationContext,\\
org.wildfly.httpclient.common.WildflyHttpContext,\\
org.xnio.DefaultXnioWorkerHolder,\\
org.xnio.channels.Channels,\\
org.xnio.nio.WorkerThread \\"

# Then other options
cmd="$cmd
--enable-url-protocols=jar,data \\
-H:ConfigurationFileDirectories=files \\
--enable-sbom=false \\
-cp \\
$JBOSS_HOME/jboss-modules.jar:\\
runtime/target/wildfly-graal-runtime-1.0-SNAPSHOT.jar:\\
wildfly-substitutions/target/wildfly-substitutions.jar:\\"

# Finally build the classpath

for (( i=0; i<${arraylength}; i++ ));
do
  line=${array[$i]}":"
  if [[ $line =~ "org/jboss/logmanager/" ]]; then
    cmd="$cmd
$line\\"
  fi
  if [[ $line =~ "org/wildfly/common" ]]; then
    cmd="$cmd
$line\\"
  fi
  if [[ $line =~ "io/smallrye/common/cpu" ]]; then
    cmd="$cmd
$line\\"
  fi
  if [[ $line =~ "io/smallrye/common/net" ]]; then
    cmd="$cmd
$line\\"
  fi
if [[ $line =~ "io/smallrye/common/os" ]]; then
    cmd="$cmd
$line\\"
  fi
if [[ $line =~ "io/smallrye/common/expression" ]]; then
    cmd="$cmd
$line\\"
  fi
if [[ $line =~ "org/jboss/logging/" ]]; then
    cmd="$cmd
$line\\"
  fi
done

echo "$cmd" > "./build-image.sh"
chmod +x ./build-image.sh
./build-image.sh