current_dir=$(pwd)

IFS=$'\n'
array=($(find ./min-core-server/modules/system/layers/base/ -name \*.jar))
unset IFS

arraylength=${#array[@]}

cmd="
native-image -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar \
-Djboss.home.dir=${current_dir}/min-core-server \
-Dlogging.configuration=file://${current_dir}/min-core-server/standalone/configuration/logging.properties \
-Dorg.jboss.boot.log.file=${current_dir}/min-core-server/standalone/log/server.log \
-Duser.home==/Users/foo \
-Djboss.server.base.dir=${current_dir}/min-core-server/standalone \
-H:+PrintClassInitialization \
--initialize-at-build-time=\
io.smallrye.common.expression,\
io.smallrye.common.expression.Expression\\\$Flag,\
jakarta.json,\
launcher.Launcher,\
org.apache.sshd.common.file.root.RootedFileSystemProvider,\
org.eclipse.parsson,\
org.jboss.as.controller,\
org.jboss.as.controller.xml,\
org.jboss.as.controller.persistence,\
org.jboss.as.controller.persistence.xml,\
org.jboss.as.jmx.model.ManagementModelIntegration,\
org.jboss.as.remoting.RemotingServices,\
org.jboss.as.repository.logging,\
org.jboss.as.server,\
org.jboss.as.version,\
org.jboss.dmr,\
org.jboss.logging,\
org.jboss.logmanager,\
org.jboss.modules,\
org.jboss.msc.service.ServiceLogger,\
org.jboss.msc.service.ServiceLogger_\\\$logger,\
org.jboss.msc.service.ServiceName,\
org.jboss.staxmapper,\
org.jboss.threads.Messages,\
org.jboss.threads.Messages_\\\$logger,\
org.jboss.vfs,\
org.slf4j.impl.Slf4jLogger,\
org.wildfly.common,\
org.wildfly.controller,\
org.wildfly.core.embedded.spi,\
org.wildfly.extension.io,\
org.wildfly.io.OptionAttributeDefinition,\
org.wildfly.openssl.OpenSSLProvider,\
org.wildfly.security,\
org.wildfly.service.descriptor,\
org.wildfly.subsystem,\
org.xnio.Option,\
org.xnio.Option\\\$1,\
org.xnio.Option\\\$2,\
org.xnio.Option\\\$3,\
org.xnio.Option\\\$4,\
org.xnio.Option\\\$5,\
org.xnio.Option\\\$6,\
org.xnio.Option\\\$7,\
org.xnio.Option\\\$8,\
org.xnio.ServiceLoaderInitializer,\
org.xnio.SingleOption,\
org.xnio.Version,\
org.xnio._private.Messages,\
org.xnio._private.Messages_\\\$logger,\
org.xnio.Xnio,\
org.xnio.nio.NioXnio\\\$DefaultSelectorCreator,\
org.xnio.nio.NioXnio\\\$4,\
org.xnio.nio.NioXnio \
--initialize-at-run-time=org.jboss.as.server.services.net,\
org.jboss.as.server.deployment.module.TempFileProviderService,\
org.jboss.as.server.DomainServerCommunicationServices,\
org.jboss.as.server.operations.NativeManagementServices \
--enable-url-protocols=jar,data \
--enable-sbom=false \
--trace-object-instantiation=org.xnio.OptionMap \
-cp ${current_dir}/min-core-server/jboss-modules.jar:"
for (( i=0; i<${arraylength}; i++ ));
do
  line=${array[$i]}":"
  #if [[ ! $line =~ "sshd" ]]; then
#    cmd="$cmd$line"
  #fi
  #if [[ ! $line =~ "bcprov-jdk18on-1.82" ]]; then
   # cmd="$cmd$line"
  #fi
#if [[  $line =~ "org/jboss/as/controller" ]]; then
#    cmd="$cmd$line"
#fi
#if [[ ! $line =~ "org/jboss/logmanager" ]]; then
#    cmd="$cmd$line"
#fi
#if [[ $line =~ "xnio" ]]; then
##    cmd="$cmd$line"
#fi
done

echo "$cmd" > "./build-image.sh"
chmod +x ./build-image.sh
./build-image.sh