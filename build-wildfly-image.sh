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
--initialize-at-build-time=org.jboss.as.jmx.model.ManagementModelIntegration,org.jboss.as.remoting.RemotingServices,org.xnio.SingleOption,org.jboss.threads.Messages,org.jboss.threads.Messages_\\\$logger,org.jboss.msc.service.ServiceLogger,org.jboss.msc.service.ServiceLogger_\\\$logger,org.jboss.as.repository.logging,org.jboss.vfs,org.jboss.as.version,org.jboss.dmr,org.wildfly.core.embedded.spi,org.jboss.as.controller,org.wildfly.service.descriptor,org.jboss.msc.service.ServiceName,org.jboss.as.controller.ProcessType,org.jboss.as.server,org.jboss.as.controller.persistence.yaml.YamlConfigurationExtension,org.jboss.as.controller.persistence.ConfigurationExtensionFactory,jakarta.json,org.eclipse.parsson,org.wildfly.openssl.OpenSSLProvider,org.wildfly.security,org.jboss.logmanager,org.wildfly.controller,launcher.Launcher,org.wildfly.common,io.smallrye.common.expression,io.smallrye.common.expression.Expression\$Flag,org.jboss.modules,org.apache.sshd.common.file.root.RootedFileSystemProvider,org.jboss.logging,org.slf4j.impl.Slf4jLogger \
--initialize-at-run-time=org.jboss.as.server.services.net,org.jboss.as.server.deployment.module.TempFileProviderService,org.jboss.as.server.DomainServerCommunicationServices,org.jboss.as.server.operations.NativeManagementServices \
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