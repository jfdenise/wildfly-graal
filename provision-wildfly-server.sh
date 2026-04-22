set -e

echo "Analyzing the deployment ${1} and provision a server to analyzer-output/wildfly-server"

rm -rf analyzer-output
java -jar analyzer/target/Analyzer-1.0-SNAPSHOT.jar ${1} analyzer.properties

echo "* Adjust the server"
analyzer-output/wildfly-server/bin/jboss-cli.sh --file=analyzer-output/graal-adjustments.cli --echo-command

if [ -n ${DEBUG} ]; then
  echo "* enable traces"
  analyzer-output/wildfly-server/bin/jboss-cli.sh --file=analyzer-output/graal-traces.cli --echo-command
fi

echo "* Deploy the deployment"
analyzer-output/wildfly-server/bin/jboss-cli.sh  --echo-command --commands="embed-server,deployment deploy-file ${1} --name=ROOT.war --runtime-name=ROOT.war --replace"


