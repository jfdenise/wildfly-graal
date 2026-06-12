# CREMA REPRODUCERS

## Get the repo

```
git clone -b crema_reproducers https://github.com/jfdenise/wildfly-graal
cd wildfly-graal
```

## Pre crema changes

* No -H:+RuntimeClassLoading
* Prior to attempt to do runtime classloading, services loaded at build time

To run it:
```
cd reproducers/no_crema
sh ./build-wildfly-native-app-image.sh
run the image : podman -p 8080:8080 wildfly-native-app-image:latest
```

## With crema changes

* -H:+RuntimeClassLoading

When running the image you should observe the XNIO problem.

To run it:
```
cd reproducers/crema
sh ./build-wildfly-native-app-image.sh
run the image : podman -p 8080:8080 wildfly-native-app-image-crema:latest
```
