current_dir=$(pwd)

IFS=$'\n'
array=($(find ./min-server2/modules/system/layers/base/ -name \*.jar))
unset IFS

arraylength=${#array[@]}

cmd="
native-image -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar \
-Djboss.home.dir=${current_dir}/min-server2 \
-Dlogging.configuration=file://${current_dir}/min-server2/standalone/configuration/logging.properties \
-Dorg.jboss.boot.log.file=${current_dir}/min-server2/standalone/log/server.log \
-Duser.home==/Users/foo \
-Djboss.server.base.dir=${current_dir}/min-server2/standalone \
--initialize-at-build-time=org.jboss.as.server,org.jboss.as.controller.persistence.yaml.YamlConfigurationExtension,org.jboss.as.controller.persistence.ConfigurationExtensionFactory,jakarta.json,org.eclipse.parsson,org.wildfly.openssl.OpenSSLProvider,org.wildfly.security,org.jboss.logmanager,org.wildfly.controller,launcher.Launcher,org.wildfly.common,io.smallrye.common.expression,io.smallrye.common.expression.Expression\$Flag,org.jboss.modules,org.apache.sshd.common.file.root.RootedFileSystemProvider,org.jboss.logging,org.slf4j.impl.Slf4jLogger \
--enable-url-protocols=jar,data \
--enable-sbom=false \
--trace-object-instantiation=org.jboss.as.server.logging.ServerLogger_$logger \
-cp ${current_dir}/min-server2/jboss-modules.jar:"
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
#if [[ $line =~ "org/jboss/as/server/" ]]; then
#    cmd="$cmd$line"
#fi
done

echo "$cmd" > "./build-image.sh"
chmod +x ./build-image.sh
./build-image.sh