FROM wildfly-native-server-builder:latest AS build
COPY --chown=1001:root --chmod=0755 ROOT.war /opt/wildfly/graal/wildfly-graal/
COPY --chown=1001:root --chmod=0755 user-content /opt/wildfly/graal/wildfly-graal/user-content
RUN cd /opt/wildfly/graal/wildfly-graal; sh ./provision-wildfly-server.sh ROOT.war
RUN cd /opt/wildfly/graal/wildfly-graal; sh ./build-wildfly-image.sh
WORKDIR /opt/wildfly/graal/wildfly-graal

FROM registry.access.redhat.com/ubi9/ubi-minimal:latest
RUN mkdir -p /opt/wildfly/graal/wildfly-graal
WORKDIR /opt/wildfly/graal/wildfly-graal
COPY --from=build /opt/wildfly/graal/wildfly-graal/analyzer-output /opt/wildfly/graal/wildfly-graal/analyzer-output 

# set up permissions for user `1001`
RUN chmod 775 /opt/wildfly/graal/wildfly-graal/analyzer-output \
  && chown -R 1001 /opt/wildfly/graal/wildfly-graal/analyzer-output \
  && chmod -R "g+rwX" /opt/wildfly/graal/wildfly-graal/analyzer-output \
  && chown -R 1001:root /opt/wildfly/graal/wildfly-graal/analyzer-output

EXPOSE 8080
USER 1001

CMD ["./wildfly-launcher.sh"]

