# Builder image

This image allows you to produce an container image containing a compiled WildFly server with your application deployed.

# How to build the image

* Copy the graal VM (linux) JVM installation in this directory, directory name must be `graalvm-jdk25`
* Call `podman build -t quay.io/jdenise/wildfly-graal-image-builder:latest .`




