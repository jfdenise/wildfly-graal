set -e

current_dir=$(pwd)
user_dir=$current_dir/user-content
mkdir -p $user_dir
image=wildfly-graal-image-builder
addons=
while getopts ":a:d:p:c:b:i:g:" arg; do
  case $arg in
    a) # Specify additional file
      IFS=',' read -r -a array <<< "$OPTARG"
      for element in "${array[@]}"
      do
        cp "$element" "$user_dir"
      done
      ;;
    d) # Deployment
      cp $OPTARG $current_dir/ROOT.war
      deploymentSet=true
      ;;
    p) # Analyzer properties, overrides default one
      cp $OPTARG $current_dir/analyzer.properties
      ;;
    c) # CLI script
      cp $OPTARG $user_dir/user-script.cli
      ;;
    b) # bash script
      cp $OPTARG $user_dir/user-script.sh
      ;;
    i) # image
      image=$OPTARG
      ;;
    g) # glow addons
      addons=$OPTARG
      ;;
    *) # unknown
      echo "Unknown argument -${OPTARG}"
      exit 1
      ;;
    
  esac
done
if [ -z "$deploymentSet" ]; then
  echo "No deployment provided, can't build the image"
  exit 1
fi

podman build --build-arg IMAGE_NAME=$image --build-arg ADDONS=$addons -t wildfly-native-app-image:latest .