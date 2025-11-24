
#files=$(`find ./min-server2/modules/system/layers/base/**/*.jar`)
IFS=$'\n'
array=($(find ./min-server2/modules/system/layers/base/ -name *.jar))
unset IFS

arraylength=${#array[@]}

cmd="
native-image -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar \
--trace-object-instantiation=org.apache.sshd.common.file.root.RootedFileSystemProvider,org.jboss.logmanager.LogContext,org.jboss.logmanager.Level \
-Djboss.home.dir=/Users/jdenise/workspaces/wildfly-graal/min-server2 \
-Djava.util.logging.manager=org.jboss.logmanager.LogManager \
-Dorg.jboss.boot.log.file=/Users/jdenise/workspaces/wildfly-graal/min-server2/standalone/log/server.log \
-Duser.home==/Users/jdenise \
-Djboss.server.base.dir=/Users/jdenise/workspaces/wildfly-graal/min-server2/standalone \
--initialize-at-build-time=org.jboss.logmanager,launcher.Launcher,org.wildfly.common._private,\
org.jboss.modules \
--enable-url-protocols=jar \
-H:ConfigurationFileDirectories=min-server-graal-agent2 \
-cp jboss-modules/target/jboss-modules-2.3.0.Final-SNAPSHOT.jar:"
for (( i=0; i<${arraylength}; i++ ));
do
  line=${array[$i]}":"
  if [[ ! $line =~ "sshd" ]]; then
    cmd="$cmd$line"
  fi
done

echo "$cmd" > "./build-image.sh"