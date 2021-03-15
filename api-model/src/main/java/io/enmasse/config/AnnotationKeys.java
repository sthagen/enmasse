/*
 * Copyright 2017-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.config;

public interface AnnotationKeys {
    String CLUSTER_ID = "cluster_id";
    String ADDRESS_SPACE = "addressSpace";
    String CERT_PROVIDER = "enmasse.io/cert-provider";
    String CERT_SECRET_NAME = "enmasse.io/cert-secret";
    String CERT_CN = "enmasse.io/cert-cn";
    String ENDPOINT_PORT = "io.enmasse.endpointPort";
    String SERVICE_NAME = "enmasse.io/service-name";
    String SERVICE_PORT_PREFIX = "enmasse.io/service-port.";
    String CREATED_BY_OLD = "io.enmasse.createdBy";
    String CREATED_BY = "enmasse.io/created-by";
    String CREATED_BY_UID = "enmasse.io/created-by-uid";
    String REALM_NAME = "enmasse.io/realm-name";
    String UUID = "enmasse.io/uuid";
    String ENDPOINT = "enmasse.io/endpoint";
    String ADDRESS = "enmasse.io/address";
    String INFRA_UUID = "enmasse.io/infra-uuid";
    String TEMPLATE_NAME = "enmasse.io/template-name";
    String QUEUE_TEMPLATE_NAME = "enmasse.io/queue-template-name";
    String TOPIC_TEMPLATE_NAME = "enmasse.io/topic-template-name";
    String APPLIED_INFRA_CONFIG = "enmasse.io/applied-infra-config";
    String OPENSHIFT_SERVING_CERT_SECRET_NAME_ALPHA = "service.alpha.openshift.io/serving-cert-secret-name";
    String OPENSHIFT_SERVING_CERT_SECRET_NAME_BETA = "service.beta.openshift.io/serving-cert-secret-name";
    String OPENSHIFT_CONNECTS_TO = "app.openshift.io/connects-to";
    String GENERATION = "enmasse.io/generation";
    String VERSION = "enmasse.io/version";
    String APPLIED_CONFIGURATION = "enmasse.io/applied-configuration";
    String APPLIED_PLAN = "enmasse.io/applied-plan";
    String ADDRESS_SPACE_NAMESPACE = "enmasse.io/address-space-namespace";
}
