#!/bin/sh
set -e
KEYCLOAK_CONFIG=${KEYCLOAK_DIR}/standalone/configuration/
OPENSHIFT_CA=${OPENSHIFT_CA:-/var/run/secrets/kubernetes.io/serviceaccount/ca.crt}
SERVICE_CA=${SERVICE_CA:-${OPENSHIFT_CA:-/var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt}}

cp ${KEYCLOAK_PLUGIN_DIR}/configuration/* ${KEYCLOAK_CONFIG}/
cp ${KEYCLOAK_PLUGIN_DIR}/configuration/${KEYCLOAK_CONFIG_FILE} ${KEYCLOAK_CONFIG}/standalone-openshift.xml
cp ${KEYCLOAK_PLUGIN_DIR}/providers/* ${KEYCLOAK_DIR}/providers/

KEYSTORE_PATH=${KEYCLOAK_DIR}/standalone/configuration/certificates.keystore
CERT_PATH=/opt/enmasse/cert
TRUSTSTORE_PATH=${KEYCLOAK_DIR}/standalone/configuration/truststore.jks

rm -f ${KEYSTORE_PATH}
openssl pkcs12 -export -passout pass:enmasse -in ${CERT_PATH}/tls.crt -inkey ${CERT_PATH}/tls.key -name "server" -out /tmp/certificates-keystore.p12
keytool -importkeystore -srcstorepass enmasse -deststorepass enmasse -destkeystore ${KEYSTORE_PATH} -srckeystore /tmp/certificates-keystore.p12 -srcstoretype PKCS12
echo "Keystore ${KEYSTORE_PATH} created"

rm -rf ${TRUSTSTORE_PATH}
cp /etc/pki/java/cacerts ${TRUSTSTORE_PATH}
chmod 644 ${TRUSTSTORE_PATH}
echo "Copied system trust store. Importing OpenShift CA ${OPENSHIFT_CA}"
keytool -import -noprompt -file ${OPENSHIFT_CA} -alias firstCA -deststorepass changeit -keystore $TRUSTSTORE_PATH
echo "Importing OpenShift Service CA ${SERVICE_CA}"
keytool -import -noprompt -file ${SERVICE_CA} -alias secondCA -deststorepass changeit -keystore $TRUSTSTORE_PATH
echo "Truststore ${TRUSTSTORE_PATH} created"
