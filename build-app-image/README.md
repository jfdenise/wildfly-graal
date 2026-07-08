
# Build a native application image for Mac and Linux

WARNING: podman must be installed on your system
By default Mac ARM image is produced, add the `-i wildfly-graal-image-builder-linux` option to build on linux

To build the image:

`sh ./build-wildfly-native-app-image.sh -d <required path to ROOT.war deployment> -i <required for linux: wildfly-graal-image-builder-linux>
-p <optional, analyzer properties file> -g <comma seperated list of Glow addOns> -c <optional, CLI script> -b <optional, bash script> -a <optional, comma separated list of files to copy in the image>`


The image `wildfly-native-app-image:latest` is produced.

To run it: `podman run -p 8080:8080 wildfly-native-app-image:latest`.


# Project demo

The demo documented in the main [README.md](../README.md) can be executed, once the deployment ROOT.war file has been built, by calling:

```
cd build-app-image
./build-wildfly-native-app-image.sh -d ../tmp/ROOT.war  -c ../demo/user-script.cli -b ../demo/user-script.sh -a ../deployment-src/custom-module/target/custom-module.jar
```
