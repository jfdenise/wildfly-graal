set -e

current_dir=$(pwd)
user_dir=$current_dir/user-content
mkdir -p $user_dir
image=wildfly-graal-image-builder-linux
cp $current_dir/../war/ROOT.war $current_dir/ROOT.war
podman build --build-arg IMAGE_NAME=wildfly-graal-image-builder-linux -t wildfly-native-app-image:latest .
