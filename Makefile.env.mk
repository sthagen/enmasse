# Docker env
DOCKER_REGISTRY     ?= quay.io
DOCKER_ORG          ?= enmasse
DOCKER              ?= docker
PROJECT_PREFIX      ?= enmasse
PROJECT_NAME        ?= $(shell basename $(CURDIR))
REVISION            ?= $(shell git rev-parse HEAD)
VERSION             ?= $(shell grep "release.version" $(TOPDIR)/pom.properties| cut -d'=' -f2)
OLM_VERSION         ?= $(shell grep "olm.version" $(TOPDIR)/pom.properties| cut -d'=' -f2)
MAVEN_VERSION       ?= $(shell grep "maven.version" $(TOPDIR)/pom.properties| cut -d'=' -f2)
APP_BUNDLE_PREFIX   ?= $(shell grep "application.bundle.prefix" $(TOPDIR)/pom.properties| cut -d'=' -f2)
OLM_PACKAGE_CHANNEL ?= $(shell grep "olm.package.channel" $(TOPDIR)/pom.properties| cut -d'=' -f2)
TAG                 ?= latest

CONSOLE_LINK_NAME         ?= $(shell grep "application.globalconsole.display.name" $(TOPDIR)/pom.properties| cut -d'=' -f2)
CONSOLE_LINK_SECTION_NAME ?= $(shell grep "application.globalconsole.section.name" $(TOPDIR)/pom.properties| cut -d'=' -f2)
CONSOLE_LINK_IMAGE_URL    ?= data:$(shell grep "olm.csv.logo.mediatype" $(TOPDIR)/pom.properties| cut -d'=' -f2);base64\,$(shell grep "olm.csv.logo.base64" $(TOPDIR)/pom.properties| cut -d'=' -f2)


# Image settings
DOCKER_REGISTRY_PREFIX ?= $(DOCKER_REGISTRY)/
IMAGE_VERSION          ?= $(TAG)
ADDRESS_SPACE_CONTROLLER_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/address-space-controller:$(IMAGE_VERSION)
STANDARD_CONTROLLER_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/standard-controller:$(IMAGE_VERSION)
ROUTER_IMAGE ?= quay.io/interconnectedcloud/qdrouterd:1.12.0
BROKER_PLUGIN_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/broker-plugin:$(IMAGE_VERSION)
TOPIC_FORWARDER_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/topic-forwarder:$(IMAGE_VERSION)
AGENT_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/agent:$(IMAGE_VERSION)
MQTT_GATEWAY_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/mqtt-gateway:$(IMAGE_VERSION)
MQTT_LWT_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/mqtt-lwt:$(IMAGE_VERSION)
NONE_AUTHSERVICE_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/none-authservice:$(IMAGE_VERSION)
KEYCLOAK_IMAGE ?= quay.io/enmasse/keycloak-openshift:4.8.3.Final
KEYCLOAK_PLUGIN_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/keycloak-plugin:$(IMAGE_VERSION)
SERVICE_BROKER_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/service-broker:$(IMAGE_VERSION)
CONSOLE_INIT_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/console-init:$(IMAGE_VERSION)
CONSOLE_SERVER_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/console-server:$(IMAGE_VERSION)
CONSOLE_PROXY_OPENSHIFT_IMAGE ?= openshift/oauth-proxy:latest
CONSOLE_PROXY_KUBERNETES_IMAGE ?= quay.io/oauth2-proxy/oauth2-proxy:v5.1.0
OLM_MANIFEST_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/olm-manifest:$(IMAGE_VERSION)
PROMETHEUS_IMAGE ?= prom/prometheus:v2.4.3
ALERTMANAGER_IMAGE ?= prom/alertmanager:v0.15.2
GRAFANA_IMAGE ?= grafana/grafana:5.3.1
APPLICATION_MONITORING_OPERATOR_IMAGE ?= quay.io/integreatly/application-monitoring-operator:v1.1.4
KUBE_STATE_METRICS_IMAGE ?= quay.io/coreos/kube-state-metrics:v1.4.0
BROKER_IMAGE ?= quay.io/enmasse/artemis-base:2.13.0

CONTROLLER_MANAGER_IMAGE   ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/controller-manager:$(IMAGE_VERSION)

IOT_AUTH_SERVICE_IMAGE                 ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-auth-service:$(IMAGE_VERSION)
IOT_DEVICE_CONNECTION_INFINISPAN_IMAGE ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-device-registry:$(IMAGE_VERSION)
IOT_DEVICE_CONNECTION_JDBC_IMAGE       ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-device-registry:$(IMAGE_VERSION)
IOT_DEVICE_REGISTRY_INFINISPAN_IMAGE   ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-device-registry:$(IMAGE_VERSION)
IOT_DEVICE_REGISTRY_JDBC_IMAGE         ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-device-registry:$(IMAGE_VERSION)
IOT_HTTP_ADAPTER_IMAGE                 ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-adapters:$(IMAGE_VERSION)
IOT_MQTT_ADAPTER_IMAGE                 ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-adapters:$(IMAGE_VERSION)
IOT_LORAWAN_ADAPTER_IMAGE              ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-adapters:$(IMAGE_VERSION)
IOT_SIGFOX_ADAPTER_IMAGE               ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-adapters:$(IMAGE_VERSION)
IOT_TENANT_CLEANER_IMAGE               ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-tenant-cleaner:$(IMAGE_VERSION)
IOT_TENANT_SERVICE_IMAGE               ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-tenant-service:$(IMAGE_VERSION)
IOT_PROXY_CONFIGURATOR_IMAGE           ?= $(DOCKER_REGISTRY_PREFIX)$(DOCKER_ORG)/iot-proxy-configurator:$(IMAGE_VERSION)

DEFAULT_PROJECT ?= enmasse-infra
ifeq ($(TAG),latest)
IMAGE_PULL_POLICY ?= Always
else
IMAGE_PULL_POLICY ?= IfNotPresent
endif

IMAGE_ENV=ADDRESS_SPACE_CONTROLLER_IMAGE=$(ADDRESS_SPACE_CONTROLLER_IMAGE) \
			STANDARD_CONTROLLER_IMAGE=$(STANDARD_CONTROLLER_IMAGE) \
			ROUTER_IMAGE=$(ROUTER_IMAGE) \
			BROKER_IMAGE=$(BROKER_IMAGE) \
			BROKER_PLUGIN_IMAGE=$(BROKER_PLUGIN_IMAGE) \
			TOPIC_FORWARDER_IMAGE=$(TOPIC_FORWARDER_IMAGE) \
			SUBSERV_IMAGE=$(SUBSERV_IMAGE) \
			SERVICE_BROKER_IMAGE=$(SERVICE_BROKER_IMAGE) \
			NONE_AUTHSERVICE_IMAGE=$(NONE_AUTHSERVICE_IMAGE) \
			AGENT_IMAGE=$(AGENT_IMAGE) \
			KEYCLOAK_IMAGE=$(KEYCLOAK_IMAGE) \
			KEYCLOAK_PLUGIN_IMAGE=$(KEYCLOAK_PLUGIN_IMAGE) \
			MQTT_GATEWAY_IMAGE=$(MQTT_GATEWAY_IMAGE) \
			MQTT_LWT_IMAGE=$(MQTT_LWT_IMAGE) \
			CONSOLE_INIT_IMAGE=$(CONSOLE_INIT_IMAGE) \
			CONSOLE_SERVER_IMAGE=$(CONSOLE_SERVER_IMAGE) \
			CONSOLE_PROXY_OPENSHIFT_IMAGE=$(CONSOLE_PROXY_OPENSHIFT_IMAGE) \
			CONSOLE_PROXY_KUBERNETES_IMAGE=$(CONSOLE_PROXY_KUBERNETES_IMAGE) \
			OLM_MANIFEST_IMAGE=$(OLM_MANIFEST_IMAGE) \
			PROMETHEUS_IMAGE=$(PROMETHEUS_IMAGE) \
			ALERTMANAGER_IMAGE=$(ALERTMANAGER_IMAGE) \
			GRAFANA_IMAGE=$(GRAFANA_IMAGE) \
			APPLICATION_MONITORING_OPERATOR_IMAGE=$(APPLICATION_MONITORING_OPERATOR_IMAGE) \
			KUBE_STATE_METRICS_IMAGE=$(KUBE_STATE_METRICS_IMAGE) \
			CONTROLLER_MANAGER_IMAGE=$(CONTROLLER_MANAGER_IMAGE) \
			IOT_PROXY_CONFIGURATOR_IMAGE=$(IOT_PROXY_CONFIGURATOR_IMAGE) \
			IOT_AUTH_SERVICE_IMAGE=$(IOT_AUTH_SERVICE_IMAGE) \
			IOT_DEVICE_CONNECTION_INFINISPAN_IMAGE=$(IOT_DEVICE_CONNECTION_INFINISPAN_IMAGE) \
			IOT_DEVICE_CONNECTION_JDBC_IMAGE=$(IOT_DEVICE_CONNECTION_JDBC_IMAGE) \
			IOT_DEVICE_REGISTRY_INFINISPAN_IMAGE=$(IOT_DEVICE_REGISTRY_INFINISPAN_IMAGE) \
			IOT_DEVICE_REGISTRY_JDBC_IMAGE=$(IOT_DEVICE_REGISTRY_JDBC_IMAGE) \
			IOT_HTTP_ADAPTER_IMAGE=$(IOT_HTTP_ADAPTER_IMAGE) \
			IOT_MQTT_ADAPTER_IMAGE=$(IOT_MQTT_ADAPTER_IMAGE) \
			IOT_LORAWAN_ADAPTER_IMAGE=$(IOT_LORAWAN_ADAPTER_IMAGE) \
			IOT_SIGFOX_ADAPTER_IMAGE=$(IOT_SIGFOX_ADAPTER_IMAGE) \
			IOT_TENANT_SERVICE_IMAGE=$(IOT_TENANT_SERVICE_IMAGE) \
			IOT_TENANT_CLEANER_IMAGE=$(IOT_TENANT_CLEANER_IMAGE) \
			IMAGE_PULL_POLICY=$(IMAGE_PULL_POLICY) \
			ENMASSE_VERSION=$(VERSION) \
			MAVEN_VERSION=$(MAVEN_VERSION) \
			IMAGE_VERSION=$(IMAGE_VERSION) \
			PROJECT_PREFIX=$(PROJECT_PREFIX)


IMAGE_LIST=\
		   $(ADDRESS_SPACE_CONTROLLER_IMAGE) \
		   $(STANDARD_CONTROLLER_IMAGE) \
		   $(ROUTER_IMAGE) \
		   $(BROKER_IMAGE) \
		   $(BROKER_PLUGIN_IMAGE) \
		   $(TOPIC_FORWARDER_IMAGE) \
		   $(SUBSERV_IMAGE) \
		   $(SERVICE_BROKER_IMAGE) \
		   $(NONE_AUTHSERVICE_IMAGE) \
		   $(AGENT_IMAGE) \
		   $(KEYCLOAK_IMAGE) \
		   $(KEYCLOAK_PLUGIN_IMAGE) \
		   $(MQTT_GATEWAY_IMAGE) \
		   $(MQTT_LWT_IMAGE) \
		   $(CONSOLE_INIT_IMAGE) \
		   $(CONSOLE_PROXY_OPENSHIFT_IMAGE) \
		   $(CONSOLE_PROXY_KUBERNETES_IMAGE) \
		   $(CONSOLE_SERVER_IMAGE) \
		   $(OLM_MANIFEST_IMAGE) \
		   $(PROMETHEUS_IMAGE) \
		   $(ALERTMANAGER_IMAGE) \
		   $(GRAFANA_IMAGE) \
		   $(APPLICATION_MONITORING_OPERATOR_IMAGE) \
		   $(KUBE_STATE_METRICS_IMAGE) \
		   $(CONTROLLER_MANAGER_IMAGE) \
		   $(IOT_PROXY_CONFIGURATOR_IMAGE) \
		   $(IOT_AUTH_SERVICE_IMAGE) \
		   $(IOT_DEVICE_CONNECTION_INFINISPAN_IMAGE) \
		   $(IOT_DEVICE_CONNECTION_JDBC_IMAGE) \
		   $(IOT_DEVICE_REGISTRY_INFINISPAN_IMAGE) \
		   $(IOT_DEVICE_REGISTRY_JDBC_IMAGE) \
		   $(IOT_HTTP_ADAPTER_IMAGE) \
		   $(IOT_MQTT_ADAPTER_IMAGE) \
		   $(IOT_LORAWAN_ADAPTER_IMAGE) \
		   $(IOT_SIGFOX_ADAPTER_IMAGE) \
		   $(IOT_TENANT_SERVICE_IMAGE) \
		   $(IOT_TENANT_CLEANER_IMAGE)

