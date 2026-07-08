# Builder image

This image allows you to produce a container image containing a compiled WildFly server with your application deployed.

# WildFly Graal VM version

Linux Aarch graalvm-25.1.3+9.1 (Downloaded July the 8th 2026)

```
native-image --version
native-image 25.0.3 2026-04-21
GraalVM Runtime Environment Oracle GraalVM 25.1.3+9.1 (build 25.0.3+9-LTS-jvmci-25.1-b19)
Substrate VM Oracle GraalVM 25.1.3+9.1 (build 25.0.3+9-LTS, serial gc, compressed references)
```

# How to build the image

* Copy the graal VM (linux) JVM installation in this directory, directory name must be `graalvm-jdk-25`
* Call `podman build -t quay.io/jdenise/wildfly-graal-image-builder:latest .`




