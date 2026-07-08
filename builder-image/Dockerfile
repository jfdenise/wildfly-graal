FROM registry.access.redhat.com/ubi9/ubi:9.8-1782841664 AS build
RUN dnf --setopt=install_weak_deps=0 --setopt=tsflags=nodocs install -y git maven tar gzip gcc glibc-devel zlib-devel shadow-utils unzip gcc-c++ findutils java-21-openjdk-devel 
RUN mkdir -p /opt/wildfly/graal/
RUN chmod 775 /opt/wildfly/graal/ \
  && chown -R 1001 /opt/wildfly/graal/ \
  && chmod -R "g+rwX" /opt/wildfly/graal/ \
  && chown -R 1001:root /opt/wildfly/graal/
RUN cd /opt/wildfly/graal && git clone -b 2026_july_end_phase2 https://github.com/jfdenise/wildfly-graal && \
git clone -b 2026_july_end_phase2 https://github.com/jfdenise/jboss-modules && \
git clone -b 2026_july_end_phase2 https://github.com/jfdenise/jboss-vfs && \
git clone -b 2026_july_end_phase2 https://github.com/jfdenise/jboss-msc && \
git clone -b 2026_july_end_phase2 https://github.com/jfdenise/xnio && \
git clone -b 2026_july_end_phase2 https://github.com/jfdenise/undertow && \
git clone -b 2026_july_end_phase2 https://github.com/jfdenise/wildfly-elytron && \
git clone -b 2026_july_end_phase2 https://github.com/jfdenise/jboss-remoting && \
git clone -b 2026_july_end_phase2 https://github.com/jfdenise/resteasy

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
RUN cd /opt/wildfly/graal && cd wildfly-graal/runtime;mvn clean install;cd ../..
RUN cd /opt/wildfly/graal && cd jboss-modules; mvn clean install -DskipTests; cd ..
RUN cd /opt/wildfly/graal && cd jboss-vfs; mvn clean install -DskipTests; cd ..
RUN cd /opt/wildfly/graal && cd jboss-msc; mvn clean install -DskipTests; cd ..
RUN cd /opt/wildfly/graal && cd xnio; mvn clean install -DskipTests; cd ..
RUN cd /opt/wildfly/graal && cd undertow; mvn clean install -DskipTests; cd ..
RUN cd /opt/wildfly/graal && cd wildfly-elytron; mvn clean install -DskipTests -DskipCompatibility=true ; cd ..
RUN cd /opt/wildfly/graal && cd jboss-remoting; mvn clean install -DskipTests; cd ..
RUN cd /opt/wildfly/graal && cd resteasy; mvn clean install -DskipTests; cd ..

RUN cd /opt/wildfly/graal && git clone -b 2026_july_end_phase2 https://github.com/jfdenise/wildfly-core
RUN cd /opt/wildfly/graal && git clone -b 2026_july_end_phase2 https://github.com/jfdenise/wildfly

RUN cd /opt/wildfly/graal && cd wildfly-core; mvn clean install -DskipTests; cd ..
RUN cd /opt/wildfly/graal && cd wildfly; mvn clean install -DskipTests; cd ..

RUN cd /opt/wildfly/graal/wildfly-graal/module-launcher; mvn clean install -DskipTests; cd ..
RUN cd /opt/wildfly/graal/wildfly-graal/agent; mvn clean install -DskipTests; cd ..
RUN cd /opt/wildfly/graal/wildfly-graal/wildfly-substitutions;mvn clean install -DskipTests;cd ..
RUN cd /opt/wildfly/graal/wildfly-graal/analyzer;mvn clean install;cd ..

COPY --chown=1001:root --chmod=0755 graalvm-jdk-25 /opt/wildfly/graal/graalvm-jdk-25
ENV GRAALVM_HOME=/opt/wildfly/graal/graalvm-jdk-25
ENV JAVA_HOME=${GRAALVM_HOME}
ENV PATH=${GRAALVM_HOME}/bin:$PATH
