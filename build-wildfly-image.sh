current_dir=$(pwd)

IFS=$'\n'
array=($(find ./min-core-server/modules/system/layers/base/ -name \*.jar))
unset IFS

arraylength=${#array[@]}

cmd="
native-image -jar module-launcher/target/ModuleLauncher-1.0-SNAPSHOT.jar \
wildfly-launcher \
-Djboss.home.dir=${current_dir}/min-core-server \
-Dlogging.configuration=file://${current_dir}/min-core-server/standalone/configuration/logging.properties \
-Dorg.jboss.boot.log.file=${current_dir}/min-core-server/standalone/log/server.log \
-Duser.home==/Users/foo \
-Djboss.server.base.dir=${current_dir}/min-core-server/standalone \
-H:+PrintClassInitialization \
--initialize-at-build-time=\
io.smallrye.common.expression,\
io.smallrye.common.expression.Expression\\\$Flag,\
io.undertow.UndertowLogger,\
io.undertow.UndertowLogger_\\\$logger,\
io.undertow.Version,\
io.undertow.server.protocol.http.ServiceLoaderInitializer,\
io.undertow.servlet.api.ServletStackTraces,\
io.undertow.util.AttachmentKey,\
io.undertow.util.ConcurrentDirectDeque,\
io.undertow.util.FastConcurrentDirectDeque,\
io.undertow.util,\
io.undertow.util.SimpleAttachmentKey,\
jakarta.json,\
launcher.Launcher,\
org.apache.sshd.common.file.root.RootedFileSystemProvider,\
org.eclipse.parsson,\
org.jboss.as.controller,\
org.jboss.as.controller.PersistentResourceDefinition,\
org.jboss.as.controller.SimpleResourceDefinition,\
org.jboss.as.controller.ResourceDefinition\\\$MinimalResourceDefinition,\
org.jboss.as.controller.xml,\
org.jboss.as.controller.persistence,\
org.jboss.as.controller.persistence.xml,\
org.jboss.as.jmx.model.ManagementModelIntegration,\
org.jboss.as.remoting.RemotingServices,\
org.jboss.as.repository.logging,\
org.jboss.as.server,\
org.jboss.as.version,\
org.jboss.dmr,\
org.jboss.jandex.DotName,\
org.jboss.jandex.DotName\\\$1,\
org.jboss.logging,\
org.jboss.logmanager,\
org.jboss.metadata.parser.servlet.Version,\
org.jboss.metadata.parser.util.Version,\
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
org.wildfly.extension.elytron.ElytronExtension,\
org.wildfly.extension.elytron.ServiceLoaderInitializer,\
org.wildfly.extension.elytron._private.WildFlyAcmeClient,\
org.wildfly.extension.elytron._private.ElytronSubsystemMessages,\
org.wildfly.extension.elytron._private.ElytronSubsystemMessages_\\\$logger,\
org.wildfly.extension.io,\
org.wildfly.extension.undertow,\
org.wildfly.extension.undertow.ServiceLoaderInitializer,\
org.wildfly.extension.undertow.AbstractHttpListenerResourceDefinition,\
org.wildfly.extension.undertow.AjpListenerResourceDefinition,\
org.wildfly.extension.undertow.HttpsListenerResourceDefinition,\
org.wildfly.extension.undertow.ListenerResourceDefinition,\
org.wildfly.extension.undertow.UndertowExtension,\
org.wildfly.extension.undertow.filters,\
org.wildfly.extension.undertow.filters.AbstractFilterDefinition,\
org.wildfly.extension.undertow.filters.ModClusterDefinition,\
org.wildfly.io.OptionAttributeDefinition,\
org.wildfly.openssl.OpenSSLProvider,\
org.wildfly.security,\
org.wildfly.service.descriptor,\
org.wildfly.subsystem,\
org.xnio.LocalSocketAddress,\
org.xnio.Option,\
org.xnio.Option\\\$1,\
org.xnio.Option\\\$2,\
org.xnio.Option\\\$3,\
org.xnio.Option\\\$4,\
org.xnio.Option\\\$5,\
org.xnio.Option\\\$6,\
org.xnio.Option\\\$7,\
org.xnio.Option\\\$8,\
org.xnio.Option\\\$9,\
org.xnio.Option\\\$10,\
org.xnio.OptionMap,\
org.xnio.SequenceOption,\
org.xnio.ServiceLoaderInitializer,\
org.xnio.SingleOption,\
org.xnio.SslClientAuthMode,\
org.xnio.Version,\
org.xnio._private.Messages,\
org.xnio._private.Messages_\\\$logger,\
org.xnio.Xnio,\
org.xnio.Xnio\\\$1,\
org.xnio.nio.Log,\
org.xnio.nio.Log_\\\$logger,\
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
-cp ${current_dir}/min-core-server/jboss-modules.jar"

echo "$cmd" > "./build-image.sh"
chmod +x ./build-image.sh
./build-image.sh