
#files=$(`find ./min-server2/modules/system/layers/base/**/*.jar`)
IFS=$'\n'
array=($(find ./min-server2/modules/system/layers/base/ -name *.jar))
unset IFS

arraylength=${#array[@]}

cmd="
native-image -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar \
-Djava.util.logging.manager=org.jboss.logmanager.LogManager \
-Djboss.home.dir=/Users/jdenise/workspaces/wildfly-graal/min-server2 \
-Dorg.jboss.boot.log.file=/Users/jdenise/workspaces/wildfly-graal/min-server2/standalone/log/server.log \
-Dlogging.configuration=file:///Users/jdenise/workspaces/graal/wildfly-graal/min-server2/standalone/standalone/logging.properties \
-Duser.home==/Users/jdenise \
-Djboss.server.base.dir=/Users/jdenise/workspaces/wildfly-graal/min-server2/standalone \
--initialize-at-build-time=launcher.Launcher,org.slf4j.helpers.NOPLogger,org.wildfly.common._private,org.jboss.logmanager,\
org.jboss.modules,org.apache.sshd.common.file.root.RootedFileSystemProvider,org.slf4j.impl.Slf4jLogger \
--enable-url-protocols=jar \
-H:ConfigurationFileDirectories=min-server-graal-agent2 \
-cp jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:"
for (( i=0; i<${arraylength}; i++ ));
do
  line=${array[$i]}":"
  cmd="$cmd$line"
done

echo "$cmd" > "./build-image.sh"