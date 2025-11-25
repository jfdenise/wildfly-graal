
#files=$(`find ./modules/**/*.jar`)
IFS=$'\n'
array=($(find ./min-server2/modules/system/layers/base/ -name *.jar))
unset IFS

arraylength=${#array[@]}

cmd="
native-image -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar \
-Djboss.home.dir=/Users/jdenise/workspaces/graal/wildfly-graal/min-server2 \
-Dlogging.configuration=file:///Users/jdenise/workspaces/graal/wildfly-graal/min-server2/standalone/configuration/logging.properties \
-Dorg.jboss.boot.log.file=/Users/jdenise/workspaces/graal/wildfly-graal/min-server2/standalone/log/server.log \
-Duser.home==/Users/jdenise \
-Djboss.server.base.dir=/Users/jdenise/workspaces/graal/wildfly-graal/min-server2/standalone \
--initialize-at-build-time=org.jboss.logmanager,launcher.Launcher,org.jboss.modules.ModularContentHandlerFactory,org.jboss.modules.DataURLStreamHandler \
--trace-object-instantiation=org.jboss.logmanager.LogManager \
-H:ConfigurationFileDirectories=min-server-graal-agent2 \
--enable-url-protocols=jar,data \
-cp jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:"
for (( i=0; i<${arraylength}; i++ ));
do
  line=${array[$i]}":"
  if [[ ! $line =~ "sshd" ]]; then
    cmd="$cmd$line"
  fi
done

echo "$cmd" > "./build-image.sh"