/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.controller;

import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.AddressSpaceSpecConnector;
import io.enmasse.address.model.AddressSpaceSpecConnectorAddressRule;
import io.enmasse.address.model.AddressSpaceSpecConnectorCredentials;
import io.enmasse.address.model.AddressSpaceSpecConnectorEndpoint;
import io.enmasse.address.model.AddressSpaceSpecConnectorTls;
import io.enmasse.address.model.AddressSpaceStatusConnector;
import io.enmasse.address.model.AddressSpaceStatusConnectorBuilder;
import io.enmasse.address.model.AddressSpaceStatusRouter;
import io.enmasse.address.model.AuthenticationServiceSettings;
import io.enmasse.address.model.KubeUtil;
import io.enmasse.address.model.Phase;
import io.enmasse.address.model.StringOrSecretSelector;
import io.enmasse.admin.model.v1.InfraConfig;
import io.enmasse.admin.model.v1.RouterPolicySpec;
import io.enmasse.admin.model.v1.StandardInfraConfig;
import io.enmasse.admin.model.v1.StandardInfraConfigSpecRouter;
import io.enmasse.config.AnnotationKeys;
import io.enmasse.config.LabelKeys;
import io.enmasse.controller.router.config.Address;
import io.enmasse.controller.router.config.AuthServicePlugin;
import io.enmasse.controller.router.config.AutoLink;
import io.enmasse.controller.router.config.Connector;
import io.enmasse.controller.router.config.Distribution;
import io.enmasse.controller.router.config.LinkDirection;
import io.enmasse.controller.router.config.LinkRoute;
import io.enmasse.controller.router.config.Listener;
import io.enmasse.controller.router.config.Policy;
import io.enmasse.controller.router.config.Role;
import io.enmasse.controller.router.config.Router;
import io.enmasse.controller.router.config.RouterConfig;
import io.enmasse.controller.router.config.SslProfile;
import io.enmasse.controller.router.config.VhostPolicy;
import io.enmasse.controller.router.config.VhostPolicyGroup;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class RouterConfigController implements Controller {
    private static final Logger log = LoggerFactory.getLogger(RouterConfigController.class);

    private final NamespacedKubernetesClient client;
    private final String namespace;
    private final AuthenticationServiceResolver authenticationServiceResolver;
    private final RouterStatusCache routerStatusCache;

    public RouterConfigController(NamespacedKubernetesClient client, String namespace, AuthenticationServiceResolver authenticationServiceResolver, RouterStatusCache routerStatusCache) {
        this.client = client;
        this.namespace = namespace;
        this.authenticationServiceResolver = authenticationServiceResolver;
        this.routerStatusCache = routerStatusCache;
    }

    public AddressSpace reconcileActive(AddressSpace addressSpace) throws Exception {
        InfraConfig infraConfig = InfraConfigs.parseCurrentInfraConfig(addressSpace);

        if (infraConfig instanceof StandardInfraConfig) {
            resetConnectorStatuses(addressSpace);
            RouterSet routerSet = RouterSet.create(namespace, addressSpace, client);
            reconcileRouterSetSecrets(addressSpace, routerSet);
            reconcileRouterConfig(addressSpace, routerSet, (StandardInfraConfig) infraConfig);
            if (routerSet.isModified()) {
                addressSpace.getStatus().setPhase(Phase.Configuring);
            }
            routerSet.apply(client);
            checkRouterConnectorStatus(addressSpace);
        }
        return addressSpace;
    }

    private static String routerConfigName(String infraUuid) {
        return "qdrouterd-config." + infraUuid;
    }

    private static ConfigMapBuilder createNewConfigMap(String infraUuid) {
        // NOTE: Deletion of this configmap is handled by ComponentFinalizerController based on these labels
        return new ConfigMapBuilder().editOrNewMetadata()
                .withName(routerConfigName(infraUuid))
                .addToLabels("app", "enmasse")
                .addToLabels("infraType", "standard")
                .addToLabels("infraUuid", infraUuid)
                .endMetadata();
    }

    private void resetConnectorStatuses(AddressSpace addressSpace) {
        List<AddressSpaceStatusConnector> connectorStatuses = addressSpace.getSpec().getConnectors()
        .stream()
        .map(connector -> new AddressSpaceStatusConnectorBuilder()
                .withName(connector.getName())
                .withReady(true)
                .build())
        .collect(Collectors.toList());
        addressSpace.getStatus().setConnectors(connectorStatuses);
    }

    private void reconcileRouterConfig(AddressSpace addressSpace, RouterSet routerSet, StandardInfraConfig infraConfig) throws IOException {
        String infraUuid = addressSpace.getAnnotation(AnnotationKeys.INFRA_UUID);
        ConfigMap config = client.configMaps().inNamespace(namespace).withName(routerConfigName(infraUuid)).get();
        RouterConfig current = null;
        if (config != null) {
            current = RouterConfig.fromMap(config.getData());
        }
        RouterConfig desired = generateConfig(addressSpace, authenticationServiceResolver.resolve(addressSpace), infraConfig);
        if (!desired.equals(current)) {
            log.debug("Router config updated. Before: '{}', After: '{}'", current, desired);
            Map<String, String> data = desired.toMap();
            if (config == null) {
                config = createNewConfigMap(infraUuid)
                        .withData(data)
                        .build();
                client.configMaps().inNamespace(namespace).withName(config.getMetadata().getName()).create(config);
            } else {
                config.setData(data);
                client.configMaps().inNamespace(namespace).withName(config.getMetadata().getName()).replace(config);

                routerSet.setModified();
            }
        }
    }

    private void reconcileRouterSetSecrets(AddressSpace addressSpace, RouterSet routerSet) {

        String infraUuid = addressSpace.getAnnotation(AnnotationKeys.INFRA_UUID);
        Map<String, String> secretToConnector = new HashMap<>();
        for (AddressSpaceSpecConnector connector : addressSpace.getSpec().getConnectors()) {
            String secretName = String.format("external-connector-%s-%s", infraUuid, connector.getName());
            Secret connectorSecret = client.secrets().inNamespace(namespace).withName(secretName).get();
            if (connectorSecret == null) {
                connectorSecret = new SecretBuilder()
                        .editOrNewMetadata()
                        .withName(secretName)
                        .withNamespace(namespace)
                        .addToLabels(LabelKeys.INFRA_UUID, infraUuid)
                        .addToLabels(LabelKeys.INFRA_TYPE, "standard")
                        .endMetadata()
                        .withType("tls")
                        .withData(new HashMap<>())
                        .build();
                reconcileConnectorSecret(connectorSecret, connector, addressSpace);
                client.secrets().inNamespace(namespace).withName(secretName).createOrReplace(connectorSecret);
            } else if (reconcileConnectorSecret(connectorSecret, connector, addressSpace)) {
                client.secrets().inNamespace(namespace).withName(secretName).createOrReplace(connectorSecret);
            }
            secretToConnector.put(secretName, connector.getName());
        }

        StatefulSet router = routerSet.getStatefulSet();
        if (routerSet.getStatefulSet() == null) {
            log.warn("Unable to find expected router statefulset {}", KubeUtil.getRouterSetName(addressSpace));
            return;
        }

        log.debug("Before volumes: {}", router.getSpec().getTemplate().getSpec().getVolumes().stream().map(Volume::getName).collect(Collectors.toSet()));

        Set<String> missingSecrets = new HashSet<>(secretToConnector.keySet());
        boolean hasChanged = false;

        // Remove secrets for non-existing connectors
        String secretPrefix = String.format("external-connector-%s-", infraUuid);
        Iterator<Volume> volumeIt = router.getSpec().getTemplate().getSpec().getVolumes().iterator();
        while (volumeIt.hasNext()) {
            Volume volume = volumeIt.next();
            if (volume.getSecret() != null) {
                if (missingSecrets.contains(volume.getSecret().getSecretName())) {
                    missingSecrets.remove(volume.getSecret().getSecretName());
                } else if (volume.getSecret().getSecretName().startsWith(secretPrefix) &&
                        !secretToConnector.keySet().contains(volume.getSecret().getSecretName())) {
                    volumeIt.remove();
                    router.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts()
                            .removeIf(m -> m.getName().equals(volume.getName()));
                    hasChanged = true;
                }
            }
        }

        // Add volume for secrets that are missing
        for (String secretName : missingSecrets) {
            router.getSpec().getTemplate().getSpec().getVolumes().add(
                new VolumeBuilder()
                    .withName(secretName)
                    .withNewSecret()
                    .withSecretName(secretName)
                    .endSecret()
                    .build());
            router.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().add(
                new VolumeMountBuilder()
                    .withMountPath(String.format("/etc/enmasse-connectors/%s", secretToConnector.get(secretName)))
                    .withName(secretName)
                    .withReadOnly(true)
                    .build());
            hasChanged = true;
        }

        log.debug("After volumes: {}", router.getSpec().getTemplate().getSpec().getVolumes().stream().map(Volume::getName).collect(Collectors.toSet()));

        if (hasChanged) {
            routerSet.setModified();
        }
    }

    private boolean reconcileConnectorSecret(Secret connectorSecret, AddressSpaceSpecConnector connector, AddressSpace addressSpace) {
        boolean needsUpdate = false;
        log.debug("Reconciling connector secret {} for connector {}", connectorSecret, connector);
        if (connector.getTls() != null) {
            if (connectorSecret.getData() == null) {
                connectorSecret.setData(new HashMap<>());
            }
            if (connector.getTls().getCaCert() != null) {
                String data = getSelectorValue(addressSpace.getMetadata().getNamespace(), connector.getTls().getCaCert(), "ca.crt").orElse(null);
                if (data == null) {
                    updateConnectorStatus(connector.getName(), false, "Unable to locate value or secret for caCert", addressSpace.getStatus().getConnectors());
                } else if (!data.equals(connectorSecret.getData().get("ca.crt"))) {
                    connectorSecret.getData().put("ca.crt", data);
                    needsUpdate = true;
                }
            }

            if (connector.getTls().getClientCert() != null) {
                String data = getSelectorValue(addressSpace.getMetadata().getNamespace(), connector.getTls().getClientCert(), "tls.crt").orElse(null);
                if (data == null) {
                    updateConnectorStatus(connector.getName(), false, "Unable to locate value or secret for clientCert", addressSpace.getStatus().getConnectors());
                } else if (!data.equals(connectorSecret.getData().get("tls.crt"))) {
                    connectorSecret.getData().put("tls.crt", data);
                    needsUpdate = true;
                }
            }

            if (connector.getTls().getClientKey() != null) {
                String data = getSelectorValue(addressSpace.getMetadata().getNamespace(), connector.getTls().getClientKey(), "tls.key").orElse(null);
                if (data == null) {
                    updateConnectorStatus(connector.getName(), false, "Unable to locate value or secret for clientKey", addressSpace.getStatus().getConnectors());
                } else if (!data.equals(connectorSecret.getData().get("tls.key"))) {
                    connectorSecret.getData().put("tls.key", data);
                    needsUpdate = true;
                }
            }
        }
        return needsUpdate;
    }

    private Optional<String> getSelectorValue(String namespace, StringOrSecretSelector selector, String defaultKey) {
        if (selector.getValue() != null) {
            return Optional.of(selector.getValue());
        } else if (selector.getValueFromSecret() != null) {
            Secret secret = client.secrets().inNamespace(namespace).withName(selector.getValueFromSecret().getName()).get();
            if (secret == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(secret.getData().get(Optional.ofNullable(selector.getValueFromSecret().getKey()).orElse(defaultKey)));
        }
        return Optional.empty();
    }

    private RouterConfig generateConfig(AddressSpace addressSpace, AuthenticationServiceSettings authServiceSettings, StandardInfraConfig infraConfig) {
        String infraUuid = addressSpace.getAnnotation(AnnotationKeys.INFRA_UUID);
        Router router = new Router();
        StandardInfraConfigSpecRouter routerSpec = infraConfig.getSpec() != null ? infraConfig.getSpec().getRouter() : null;
        if (routerSpec != null && routerSpec.getWorkerThreads() != null) {
            router.setWorkerThreads(routerSpec.getWorkerThreads());
        }

        // SSL Profiles
        List<SslProfile> sslProfiles = new ArrayList<>();
        SslProfile authServiceSsl = new SslProfile();
        authServiceSsl.setName("auth_service_ssl");
        authServiceSsl.setCaCertFile("/etc/qpid-dispatch/authservice-ca/tls.crt");
        sslProfiles.add(authServiceSsl);

        SslProfile sslDetails = new SslProfile();
        sslDetails.setName("ssl_details");
        sslDetails.setCertFile("/etc/qpid-dispatch/ssl/tls.crt");
        sslDetails.setPrivateKeyFile("/etc/qpid-dispatch/ssl/tls.key");
        sslDetails.setProtocols("TLSv1.2");
        sslProfiles.add(sslDetails);

        SslProfile interRouterTls = new SslProfile();
        interRouterTls.setName("inter_router_tls");
        interRouterTls.setCaCertFile("/etc/enmasse-certs/ca.crt");
        interRouterTls.setCertFile("/etc/enmasse-certs/tls.crt");
        interRouterTls.setPrivateKeyFile("/etc/enmasse-certs/tls.key");
        sslProfiles.add(interRouterTls);

        // Authenticationservice plugin
        AuthServicePlugin authService = new AuthServicePlugin();
        authService.setName("auth_service");
        authService.setHost(authServiceSettings.getHost());
        authService.setPort(authServiceSettings.getPort());
        authService.setRealm(authServiceSettings.getRealm());
        authService.setSslProfile("auth_service_ssl");

        // Listeners
        Listener localBypass = new Listener();
        localBypass.setHost("127.0.0.1");
        localBypass.setPort(7777);
        localBypass.setAuthenticatePeer(false);

        Listener livenessProbe = new Listener();
        livenessProbe.setHost("127.0.0.1");
        livenessProbe.setPort(7770);
        livenessProbe.setAuthenticatePeer(false);
        livenessProbe.setHttp(true);
        livenessProbe.setMetrics(false);
        livenessProbe.setHealthz(true);
        livenessProbe.setWebsockets(false);
        livenessProbe.setHttpRootDir("invalid");

        Listener interRouter = new Listener();
        interRouter.setHost("0.0.0.0");
        interRouter.setPort(55672);
        interRouter.setRole(Role.inter_router);
        interRouter.setAuthenticatePeer(true);
        interRouter.setSslProfile("inter_router_tls");
        interRouter.setSaslMechanisms("EXTERNAL");
        if (routerSpec != null) {
            if (routerSpec.getIdleTimeout() != null) {
                interRouter.setIdleTimeoutSeconds(routerSpec.getIdleTimeout());
            }

            if (routerSpec.getLinkCapacity() != null) {
                interRouter.setLinkCapacity(routerSpec.getLinkCapacity());
            }
        }

        Listener httpsPublic = new Listener();
        httpsPublic.setHost("0.0.0.0");
        httpsPublic.setPort(8443);
        httpsPublic.setSaslPlugin("auth_service");
        httpsPublic.setSslProfile("ssl_details");
        httpsPublic.setHttp(true);
        httpsPublic.setAuthenticatePeer(true);
        if (routerSpec != null && routerSpec.getPolicy() != null) {
            httpsPublic.setPolicyVhost("public");
        }

        if (routerSpec != null) {
            if (routerSpec.getIdleTimeout() != null) {
                httpsPublic.setIdleTimeoutSeconds(routerSpec.getIdleTimeout());
            }

            if (routerSpec.getLinkCapacity() != null) {
                httpsPublic.setLinkCapacity(routerSpec.getLinkCapacity());
            }
        }

        Listener amqpPublic = new Listener();
        amqpPublic.setHost("0.0.0.0");
        amqpPublic.setPort(5672);
        amqpPublic.setSaslPlugin("auth_service");
        amqpPublic.setAuthenticatePeer(true);

        if (routerSpec != null && routerSpec.getPolicy() != null) {
            amqpPublic.setPolicyVhost("public");
        }

        if (routerSpec != null) {
            if (routerSpec.getIdleTimeout() != null) {
                amqpPublic.setIdleTimeoutSeconds(routerSpec.getIdleTimeout());
            }

            if (routerSpec.getLinkCapacity() != null) {
                amqpPublic.setLinkCapacity(routerSpec.getLinkCapacity());
            }

            if (routerSpec.getHandshakeTimeout() != null) {
                amqpPublic.setInitialHandshakeTimeoutSeconds(routerSpec.getHandshakeTimeout());
            }
        }

        Listener amqpsPublic = new Listener();
        amqpsPublic.setHost("0.0.0.0");
        amqpsPublic.setPort(5671);
        amqpsPublic.setSaslPlugin("auth_service");
        amqpsPublic.setSslProfile("ssl_details");
        amqpsPublic.setRequireSsl(true);
        amqpsPublic.setAuthenticatePeer(true);

        if (routerSpec != null && routerSpec.getPolicy() != null) {
            amqpsPublic.setPolicyVhost("public");
        }

        if (routerSpec != null) {
            if (routerSpec.getIdleTimeout() != null) {
                amqpsPublic.setIdleTimeoutSeconds(routerSpec.getIdleTimeout());
            }

            if (routerSpec.getLinkCapacity() != null) {
                amqpsPublic.setLinkCapacity(routerSpec.getLinkCapacity());
            }

            if (routerSpec.getHandshakeTimeout() != null) {
                amqpsPublic.setInitialHandshakeTimeoutSeconds(routerSpec.getHandshakeTimeout());
            }
        }


        Listener amqpsInternal = new Listener();
        amqpsInternal.setHost("0.0.0.0");
        amqpsInternal.setPort(55671);
        amqpsInternal.setSslProfile("inter_router_tls");
        amqpsInternal.setRequireSsl(true);
        amqpsInternal.setSaslMechanisms("EXTERNAL");
        amqpsInternal.setAuthenticatePeer(true);

        if (routerSpec != null) {
            if (routerSpec.getIdleTimeout() != null) {
                amqpsInternal.setIdleTimeoutSeconds(routerSpec.getIdleTimeout());
            }

            if (routerSpec.getLinkCapacity() != null) {
                amqpsInternal.setLinkCapacity(routerSpec.getLinkCapacity());
            }
        }

        Listener amqpsRouteContainer = new Listener();
        amqpsRouteContainer.setHost("0.0.0.0");
        amqpsRouteContainer.setPort(56671);
        amqpsRouteContainer.setSslProfile("inter_router_tls");
        amqpsRouteContainer.setRole(Role.route_container);
        amqpsRouteContainer.setRequireSsl(true);
        amqpsRouteContainer.setSaslMechanisms("EXTERNAL");
        amqpsRouteContainer.setAuthenticatePeer(true);

        if (routerSpec != null) {
            if (routerSpec.getIdleTimeout() != null) {
                amqpsRouteContainer.setIdleTimeoutSeconds(routerSpec.getIdleTimeout());
            }

            if (routerSpec.getLinkCapacity() != null) {
                amqpsRouteContainer.setLinkCapacity(routerSpec.getLinkCapacity());
            }
        }

        // Policies
        List<Policy> policies = Collections.emptyList();
        if (routerSpec != null && routerSpec.getPolicy() != null) {
            Policy policy = new Policy();
            policy.setEnableVhostPolicy(true);
            policies = Collections.singletonList(policy);
        }

        // VhostPolicy
        List<VhostPolicy> vhostPolicies = Collections.emptyList();
        if (routerSpec != null && routerSpec.getPolicy() != null) {
            vhostPolicies = createVhostPolices(routerSpec.getPolicy());
        }

        // Connectors
        List<Connector> connectors = new ArrayList<>();
        Connector ragentConnector = new Connector();
        ragentConnector.setHost("ragent-" + infraUuid);
        ragentConnector.setPort(5671);
        ragentConnector.setSslProfile("inter_router_tls");
        ragentConnector.setVerifyHostname(false);
        connectors.add(ragentConnector);

        // Addresses
        List<Address> addresses = new ArrayList<>();
        Address tempAddress = new Address();
        tempAddress.setName("override.temp");
        tempAddress.setPrefix("$temp");
        tempAddress.setDistribution(Distribution.balanced);
        addresses.add(tempAddress);

        if (infraConfig.getSpec().getGlobalDLQ() != null && infraConfig.getSpec().getGlobalDLQ()) {
            Address dlqAddress = new Address();
            dlqAddress.setName("override.global.dlq");
            dlqAddress.setPattern("!!GLOBAL_DLQ");
            dlqAddress.setDistribution(Distribution.balanced);
            dlqAddress.setWaypoint(true);
            addresses.add(dlqAddress);
        }

        Address routerHealthCheckAddress = new Address();
        routerHealthCheckAddress.setName("!!HEALTH_CHECK_ROUTER");
        routerHealthCheckAddress.setPrefix("!!HEALTH_CHECK_ROUTER");
        routerHealthCheckAddress.setDistribution(Distribution.balanced);
        addresses.add(routerHealthCheckAddress);

        // Autolinks
        List<AutoLink> autoLinks = new ArrayList<>();
        if (infraConfig.getSpec().getGlobalDLQ() != null && infraConfig.getSpec().getGlobalDLQ()) {
            AutoLink dlqAutoLink = new AutoLink();
            dlqAutoLink.setName("override.global.dlq.in");
            dlqAutoLink.setAddress("!!GLOBAL_DLQ");
            dlqAutoLink.setDirection(LinkDirection.in);
            dlqAutoLink.setContainerId("broker-global-dlq-out");
            autoLinks.add(dlqAutoLink);
        }

        // LinkRoutes
        List<LinkRoute> linkRoutes = new ArrayList<>();

        // Connectors and addresses based on Connectors configured by user
        for (AddressSpaceSpecConnector connector : addressSpace.getSpec().getConnectors()) {
            Connector remoteConnector = new Connector();
            remoteConnector.setName(connector.getName());
            AddressSpaceStatusConnector connectorStatus = addressSpace.getStatus().getConnectors().stream()
                    .filter(status -> status.getName().equals(connector.getName()))
                    .findFirst().orElse(null);

            // Don't bother adding connector status if it already failed
            if (connectorStatus == null || !connectorStatus.isReady()) {
                continue;
            }

            Iterator<AddressSpaceSpecConnectorEndpoint> endpointIt = connector.getEndpointHosts().iterator();
            // If connector is missing initial host:
            if (!endpointIt.hasNext()) {
                updateConnectorStatus(connector.getName(), false, "Missing at least one endpoint on connector", addressSpace.getStatus().getConnectors());
                continue;
            }

            AddressSpaceSpecConnectorEndpoint first = endpointIt.next();
            remoteConnector.setHost(first.getHost());
            remoteConnector.setPort(connector.getPort(first.getPort()));

            List<String> failoverUrls = new ArrayList<>();
            while (endpointIt.hasNext()) {
                AddressSpaceSpecConnectorEndpoint failover = endpointIt.next();
                String protocol = "amqp" + (connector.getTls() != null ? "s" : "");
                failoverUrls.add(String.format("%s://%s:%d", protocol, failover.getHost(), connector.getPort(failover.getPort())));
            }
            if (!failoverUrls.isEmpty()) {
                remoteConnector.setFailoverUrls(String.join(",", failoverUrls));
            }

            List<String> saslMechanisms = new ArrayList<>();

            // If tls field is set, we will enable TLS
            AddressSpaceSpecConnectorTls tls = connector.getTls();
            if (tls != null) {
                String sslProfileName = String.format("connector_%s_settings", connector.getName());
                remoteConnector.setSslProfile(sslProfileName);

                SslProfile sslProfile = new SslProfile();
                sslProfile.setName(sslProfileName);

                if (tls.getCaCert() != null) {
                    sslProfile.setCaCertFile(String.format("/etc/enmasse-connectors/%s/ca.crt", connector.getName()));
                }

                if (tls.getClientCert() != null || tls.getClientKey() != null) {
                    if (tls.getClientCert() == null) {
                        updateConnectorStatus(connector.getName(), false, "Both clientCert and clientKey must be specified (only clientKey is specified)", addressSpace.getStatus().getConnectors());
                        continue;
                    }

                    if (tls.getClientKey() == null) {
                        updateConnectorStatus(connector.getName(), false, "Both clientCert and clientKey must be specified (only clientCert is specified)", addressSpace.getStatus().getConnectors());
                        continue;
                    }

                    sslProfile.setCertFile(String.format("/etc/enmasse-connectors/%s/tls.crt", connector.getName()));
                    sslProfile.setPrivateKeyFile(String.format("/etc/enmasse-connectors/%s/tls.key", connector.getName()));
                }

                sslProfiles.add(sslProfile);
                saslMechanisms.add("EXTERNAL");
            }

            AddressSpaceSpecConnectorCredentials credentials = connector.getCredentials();
            if (credentials != null) {
                if (connector.getCredentials().getUsername() != null) {
                    String data = getSelectorValue(addressSpace.getMetadata().getNamespace(), connector.getCredentials().getUsername(), "username").orElse(null);
                    if (data == null) {
                        updateConnectorStatus(connector.getName(), false, "Unable to locate value or secret for username", addressSpace.getStatus().getConnectors());
                        continue;
                    }
                    remoteConnector.setSaslUsername(data);
                }

                if (connector.getCredentials().getPassword() != null) {
                    String data = getSelectorValue(addressSpace.getMetadata().getNamespace(), connector.getCredentials().getPassword(), "password").orElse(null);
                    if (data == null) {
                        updateConnectorStatus(connector.getName(), false, "Unable to locate value or secret for password", addressSpace.getStatus().getConnectors());
                        continue;
                    }
                    remoteConnector.setSaslPassword(data);
                }

                saslMechanisms.add("PLAIN");
            }

            if (saslMechanisms.isEmpty()) {
                saslMechanisms.add("ANONYMOUS");
            }

            remoteConnector.setSaslMechanisms(String.join(" ", saslMechanisms));

            String prefix = String.format("%s/", connector.getName());
            for (AddressSpaceSpecConnectorAddressRule rule : connector.getAddresses()) {
                LinkRoute linkRouteIn = new LinkRoute();
                linkRouteIn.setName("override.connector." + connector.getName() + "." + rule.getName() + ".in");
                linkRouteIn.setPattern(prefix + rule.getPattern());
                linkRouteIn.setDelExternalPrefix(prefix);
                linkRouteIn.setDirection(LinkDirection.in);
                linkRouteIn.setConnection(connector.getName());
                linkRoutes.add(linkRouteIn);

                LinkRoute linkRouteOut = new LinkRoute();
                linkRouteOut.setName("override.connector." + connector.getName() + "." + rule.getName() + ".out");
                linkRouteOut.setPattern(connector.getName() + "/" + rule.getPattern());
                linkRouteOut.setDelExternalPrefix(prefix);
                linkRouteOut.setDirection(LinkDirection.out);
                linkRouteOut.setConnection(connector.getName());
                linkRoutes.add(linkRouteOut);
            }

            remoteConnector.setRole(Role.forValue(connector.getRole()));

            if (connector.getIdleTimeout() != null) {
                remoteConnector.setIdleTimeoutSeconds(connector.getIdleTimeout());
            }

            if (connector.getMaxFrameSize() != null) {
                remoteConnector.setMaxFrameSize(Math.max(512 /* AMQP MIN-MAX-FRAME-SIZE */ , connector.getMaxFrameSize()));
            }

            connectors.add(remoteConnector);
        }

        return new RouterConfig(router,
                sslProfiles,
                Collections.singletonList(authService),
                Arrays.asList(localBypass, livenessProbe, httpsPublic, amqpPublic, amqpsPublic, amqpsInternal, amqpsRouteContainer, interRouter),
                policies,
                connectors,
                autoLinks,
                linkRoutes,
                addresses,
                vhostPolicies);
    }

    static List<VhostPolicy> createVhostPolices(RouterPolicySpec policy) {
        // Public settings derived from infra config settings
        VhostPolicyGroup group = new VhostPolicyGroup();
        group.setRemoteHosts("*");
        group.setSources("*");
        group.setTargets("*");
        group.setAllowAnonymousSender(true);
        group.setAllowDynamicSource(true);

        if (policy.getMaxSessionsPerConnection() != null) {
            group.setMaxSessions(policy.getMaxSessionsPerConnection());
        }

        if (policy.getMaxSendersPerConnection() != null) {
            group.setMaxSenders(policy.getMaxSendersPerConnection());
        }

        if (policy.getMaxReceiversPerConnection() != null) {
            group.setMaxReceivers(policy.getMaxReceiversPerConnection());
        }

        VhostPolicy vhostPolicy = new VhostPolicy();
        vhostPolicy.setHostname("public");
        vhostPolicy.setAllowUnknownUser(true);

        if (policy.getMaxConnections() != null) {
            vhostPolicy.setMaxConnections(policy.getMaxConnections());
        }

        if (policy.getMaxConnectionsPerHost() != null) {
            vhostPolicy.setMaxConnectionsPerHost(policy.getMaxConnectionsPerHost());
        }

        if (policy.getMaxConnectionsPerUser() != null) {
            vhostPolicy.setMaxConnectionsPerUser(policy.getMaxConnectionsPerUser());
        }

        if (policy.getMaxMessageSize() != null) {
            vhostPolicy.setMaxMessageSize(policy.getMaxMessageSize());
        }

        vhostPolicy.setGroups(Collections.singletonMap("$default", group));

        VhostPolicyGroup internalGroup = new VhostPolicyGroup();
        internalGroup.setRemoteHosts("*");
        internalGroup.setSources("*");
        internalGroup.setTargets("*");
        internalGroup.setAllowDynamicSource(true);
        internalGroup.setAllowAnonymousSender(true);

        VhostPolicy internalVhostPolicy = new VhostPolicy();
        internalVhostPolicy.setHostname("$default");
        internalVhostPolicy.setAllowUnknownUser(true);
        internalVhostPolicy.setGroups(Collections.singletonMap("$default", internalGroup));

        return Arrays.asList(internalVhostPolicy, vhostPolicy);
    }

    private static void updateConnectorStatus(String name, boolean isReady, String message, List<AddressSpaceStatusConnector> connectorStatuses) {
        connectorStatuses.stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().ifPresent(s -> {
            s.setReady(isReady);
            s.appendMessage(message);
        });
    }

    void checkRouterConnectorStatus(AddressSpace addressSpace) {
        Map<String, AddressSpaceSpecConnector> connectorMap = new HashMap<>();
        for (AddressSpaceSpecConnector connector : addressSpace.getSpec().getConnectors()) {
            connectorMap.put(connector.getName(), connector);
        }

        List<RouterStatus> routerStatusList = routerStatusCache.getLatestResult(addressSpace);

        for (AddressSpaceStatusConnector connector : addressSpace.getStatus().getConnectors()) {
            checkConnectorStatus(connector, connectorMap.get(connector.getName()), routerStatusList);
        }
    }

    /*
     * Until the connector entity allows querying for the status, we have to go through all connections and
     * see if we can find our connector host in there.
     */
    private void checkConnectorStatus(AddressSpaceStatusConnector connectorStatus, AddressSpaceSpecConnector connector, List<RouterStatus> response) {
        Map<String, ConnectionStatus> connectionStatuses = new HashMap<>();
        for (AddressSpaceSpecConnectorEndpoint endpoint : connector.getEndpointHosts()) {
            String host = String.format("%s:%d", endpoint.getHost(), connector.getPort(endpoint.getPort()));
            connectionStatuses.put(host, new ConnectionStatus());
        }

        if (response == null || response.isEmpty()) {
            connectorStatus.setReady(false);
            connectorStatus.appendMessage("No router status found.");
            return;
        }

        for (RouterStatus routerStatus : response) {
            List<String> hosts = routerStatus.getConnections().getHosts();
            List<Boolean> opened = routerStatus.getConnections().getOpened();
            List<String> operStatus = routerStatus.getConnections().getOperStatus();

            for (int i = 0; i < hosts.size(); i++) {
                ConnectionStatus status = connectionStatuses.get(hosts.get(i));
                if (status != null) {
                    status.setFound(true);
                    if (operStatus.get(i).equals("up")) {
                        status.setConnected(true);
                    }
                    if (opened.get(i)) {
                        status.setOpened(true);
                    }
                }
            }
        }

        // Assumption/decision: If the primary or failover for any connector is up, we are ok
        List<ConnectionStatus> found = connectionStatuses.values().stream()
                .filter(ConnectionStatus::isFound)
                .collect(Collectors.toList());

        List<ConnectionStatus> isConnected = found.stream()
                .filter(ConnectionStatus::isConnected)
                .collect(Collectors.toList());

        List<ConnectionStatus> isOpened = isConnected.stream()
                .filter(ConnectionStatus::isOpened)
                .collect(Collectors.toList());

        if (found.isEmpty()) {
            connectorStatus.setReady(false);
            connectorStatus.appendMessage("Unable to find active connection for connector '" + connector.getName() + "'");
            return;
        }

        if (isConnected.isEmpty()) {
            connectorStatus.setReady(false);
            connectorStatus.appendMessage("Unable to find connection in the connected state for connector '" + connector.getName() + "'");
        }

        if (isOpened.isEmpty()) {
            connectorStatus.setReady(false);
            connectorStatus.appendMessage("Unable to find connection in the opened state for connector '" + connector.getName() + "'");
        }
    }

    private void checkRouterMesh(AddressSpace addressSpace, List<RouterStatus> routerStatusList) {
        final List<AddressSpaceStatusRouter> routers = new ArrayList<>();
        Set<String> routerIds = routerStatusList.stream().map(RouterStatus::getRouterId).collect(Collectors.toSet());

        for (RouterStatus routerStatus : routerStatusList) {
            String routerId = routerStatus.getRouterId();
            List<String> neighbors = new ArrayList<>(routerStatus.getNeighbors());
            // Add ourselves to make the comparison simpler
            neighbors.add(routerId);

            if (!neighbors.containsAll(routerIds)) {
                Set<String> missing = new HashSet<>(routerIds);
                missing.removeAll(neighbors);
                String msg = String.format("Router %s is missing connection to %s.", routerId, missing);
                log.warn(msg);
                addressSpace.getStatus().setReady(false);
                addressSpace.getStatus().appendMessage(msg);
            }

            AddressSpaceStatusRouter addressSpaceStatusRouter = new AddressSpaceStatusRouter();
            addressSpaceStatusRouter.setId(routerId);
            addressSpaceStatusRouter.setNeighbors(neighbors);
            addressSpaceStatusRouter.setUndelivered(routerStatus.getUndelivered());

            log.debug("Router {} has neighbors: {} and undelivered: {}", routerId, neighbors, routerStatus.getUndelivered());
            routers.add(addressSpaceStatusRouter);
        }
        addressSpace.getStatus().setRouters(routers);
    }

    private static class ConnectionStatus {
        private boolean isFound = false;
        private boolean isConnected = false;
        private boolean isOpened = false;

        boolean isConnected() {
            return isConnected;
        }

        void setConnected(boolean connected) {
            isConnected = connected;
        }

        boolean isOpened() {
            return isOpened;
        }

        void setOpened(boolean opened) {
            isOpened = opened;
        }

        boolean isFound() {
            return isFound;
        }

        void setFound(boolean found) {
            isFound = found;
        }
    }

    @Override
    public String toString() {
        return "RouterConfigController";
    }
}
