/*
 * Copyright 2017-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.k8s.api;

import io.enmasse.address.model.*;
import io.enmasse.admin.model.AddressPlan;
import io.enmasse.admin.model.AddressSpacePlan;
import io.enmasse.admin.model.v1.AddressPlanStatus;
import io.enmasse.admin.model.v1.AddressPlanStatusBuilder;
import io.enmasse.admin.model.v1.AuthenticationService;
import io.enmasse.admin.model.v1.*;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class KubeSchemaApi implements SchemaApi {

    private static final Logger log = LoggerFactory.getLogger(KubeSchemaApi.class);
    private final CrdApi<io.enmasse.admin.model.v1.AddressSpacePlan> addressSpacePlanApi;
    private final CrdApi<io.enmasse.admin.model.v1.AddressPlan> addressPlanApi;
    private final CrdApi<BrokeredInfraConfig> brokeredInfraConfigApi;
    private final CrdApi<StandardInfraConfig> standardInfraConfigApi;
    private final CrdApi<AuthenticationService> authenticationServiceApi;
    private final String defaultVersion;

    private final Clock clock;
    private static final DateTimeFormatter formatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneId.of("UTC"));
    private final boolean isOpenShift;
    private final boolean updateStatus;

    private volatile List<io.enmasse.admin.model.v1.AddressSpacePlan> currentAddressSpacePlans = Collections.emptyList();
    private volatile List<io.enmasse.admin.model.v1.AddressPlan> currentAddressPlans = Collections.emptyList();
    private volatile List<StandardInfraConfig> currentStandardInfraConfigs = Collections.emptyList();
    private volatile List<BrokeredInfraConfig> currentBrokeredInfraConfigs = Collections.emptyList();
    private volatile List<AuthenticationService> currentAuthenticationServices = Collections.emptyList();

    public KubeSchemaApi(CrdApi<io.enmasse.admin.model.v1.AddressSpacePlan> addressSpacePlanApi,
                         CrdApi<io.enmasse.admin.model.v1.AddressPlan> addressPlanApi,
                         CrdApi<BrokeredInfraConfig> brokeredInfraConfigApi,
                         CrdApi<StandardInfraConfig> standardInfraConfigApi,
                         CrdApi<AuthenticationService> authenticationServiceApi,
                         String defaultVersion, Clock clock,
                         boolean isOpenShift, boolean updateStatus) {
        this.addressSpacePlanApi = addressSpacePlanApi;
        this.addressPlanApi = addressPlanApi;
        this.brokeredInfraConfigApi = brokeredInfraConfigApi;
        this.standardInfraConfigApi = standardInfraConfigApi;
        this.authenticationServiceApi = authenticationServiceApi;
        this.defaultVersion = defaultVersion;
        this.clock = clock;
        this.isOpenShift = isOpenShift;
        this.updateStatus = updateStatus;
    }

    public static KubeSchemaApi create(NamespacedKubernetesClient openShiftClient, String namespace, String defaultVersion, boolean isOpenShift, boolean updateStatus) {
        CrdApi<io.enmasse.admin.model.v1.AddressSpacePlan> addressSpacePlanApi = new KubeCrdApi<>(openShiftClient, namespace, AdminCrd.addressSpacePlans(),
                io.enmasse.admin.model.v1.AddressSpacePlan.class,
                AddressSpacePlanList.class);

        CrdApi<io.enmasse.admin.model.v1.AddressPlan> addressPlanApi = new KubeCrdApi<>(openShiftClient, namespace, AdminCrd.addressPlans(),
                io.enmasse.admin.model.v1.AddressPlan.class,
                AddressPlanList.class
        );

        CrdApi<BrokeredInfraConfig> brokeredInfraConfigApi = new KubeCrdApi<>(openShiftClient, namespace, AdminCrd.brokeredInfraConfigs(),
                BrokeredInfraConfig.class,
                BrokeredInfraConfigList.class
        );

        CrdApi<StandardInfraConfig> standardInfraConfigApi = new KubeCrdApi<>(openShiftClient, namespace, AdminCrd.standardInfraConfigs(),
                StandardInfraConfig.class,
                StandardInfraConfigList.class
        );

        CrdApi<AuthenticationService> authenticationServiceApi = new KubeCrdApi<>(openShiftClient, namespace, AdminCrd.authenticationServices(),
                AuthenticationService.class,
                AuthenticationServiceList.class
        );

        Clock clock = Clock.systemUTC();

        return new KubeSchemaApi(addressSpacePlanApi, addressPlanApi, brokeredInfraConfigApi, standardInfraConfigApi, authenticationServiceApi, defaultVersion, clock, isOpenShift, updateStatus);
    }

    private void validateAddressSpacePlan(AddressSpacePlan addressSpacePlan, List<AddressPlan> addressPlans, List<String> infraTemplateNames) {
        String infraConfigRef = addressSpacePlan.getInfraConfigRef();
        if (!infraTemplateNames.contains(infraConfigRef)) {
            String error = "Error validating address space plan " + addressSpacePlan.getMetadata().getName() + ": missing infra config definition " + infraConfigRef + ", found: " + infraTemplateNames;
            log.warn(error);
            throw new SchemaValidationException(error);
        }

        Set<String> addressPlanNames = addressPlans.stream().map(p -> p.getMetadata().getName()).collect(Collectors.toSet());
        if (!addressPlanNames.containsAll(addressSpacePlan.getAddressPlans())) {
            Set<String> missing = new HashSet<>(addressSpacePlan.getAddressPlans());
            missing.removeAll(addressPlanNames);
            String error = "Error validating address space plan " + addressSpacePlan.getMetadata().getName() + ": missing " + missing;
            log.warn(error);
            throw new SchemaValidationException(error);
        }

        Set<String> resources = addressSpacePlan.getResourceLimits().keySet();
        List<String> required = "brokered".equals(addressSpacePlan.getAddressSpaceType()) ? Arrays.asList("broker") : Arrays.asList("broker", "router", "aggregate");
        if (!resources.containsAll(required)) {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(resources);
            String error = "Error validating address space plan " + addressSpacePlan.getMetadata().getName() + ": missing resources " + missing;
            log.warn(error);
            throw new SchemaValidationException(error);
        }
    }

    private void validateAddressPlan(String addressSpaceType, AddressPlan addressPlan) {

        List<String> supportedTypes = new ArrayList<>();
        List<String> requiredResources = new ArrayList<>();
        if ("brokered".equals(addressSpaceType)) {
            supportedTypes.addAll(Arrays.asList("queue", "topic", "deadletter"));
            if (!"deadletter".equals(addressPlan.getAddressType())) {
                requiredResources.add("broker");
            }
        } else if ("standard".equals(addressSpaceType)) {
            supportedTypes.addAll(Arrays.asList("queue", "topic", "subscription", "anycast", "multicast", "deadletter"));
            if (!"deadletter".equals(addressPlan.getAddressType())) {
                requiredResources.add("router");
            }
            if (!Arrays.asList("anycast", "multicast", "deadletter").contains(addressPlan.getAddressType())) {
                if (!"queue".equals(addressPlan.getAddressType()) && addressPlan.getPartitions() > 1) {
                    String error = "Error validating address plan " + addressPlan.getMetadata().getName() + ": address type " + addressPlan.getAddressType() + " does not support more than 1 partition";
                    log.warn(error);
                    throw new SchemaValidationException(error);
                }
                requiredResources.add("broker");
            }
        }
        Set<String> resourcesUsed = addressPlan.getResources().keySet();

        if (!resourcesUsed.containsAll(requiredResources)) {
            Set<String> missing = new HashSet<>(requiredResources);
            missing.removeAll(resourcesUsed);
            String error = "Error validating address plan " + addressPlan.getMetadata().getName() + ": missing resources " + missing;
            log.warn(error);
            throw new SchemaValidationException(error);
        }

        if (!supportedTypes.contains(addressPlan.getAddressType())) {
            String error = String.format("Error validating address plan %s: address type %s not supported by address space type %s", addressPlan.getMetadata().getName(), addressPlan.getAddressType(), addressSpaceType);
            log.warn(error);
            throw new SchemaValidationException(error);
        }
    }

    private EndpointSpec createEndpointSpec(String name, String service, String port, TlsTermination tlsTermination) {
        if (isOpenShift) {
            return new EndpointSpecBuilder()
                    .withName(name)
                    .withService(service)
                    .withExpose(new ExposeSpecBuilder()
                            .withType(ExposeType.route)
                            .withRouteTlsTermination(tlsTermination)
                            .withRouteServicePort(port)
                            .build())
                    .build();
        } else {
            return new EndpointSpecBuilder()
                    .withName(name)
                    .withService(service)
                    .withExpose(new ExposeSpecBuilder()
                            .withType(ExposeType.loadbalancer)
                            .withLoadBalancerPorts(Collections.singletonList(port))
                            .build())
                    .build();
        }
    }

    private AddressSpaceType createStandardType(List<AddressSpacePlan> addressSpacePlans, Collection<AddressPlan> addressPlans, List<InfraConfig> standardInfraConfigs) {
        AddressSpaceTypeBuilder builder = new AddressSpaceTypeBuilder();
        builder.withName("standard");
        builder.withDescription("A standard address space consists of an AMQP router network in combination with " +
                "attachable 'storage units'. The implementation of a storage unit is hidden from the client " +
                        "and the routers with a well defined API.");

        builder.withAvailableEndpoints(Arrays.asList(
                createEndpointSpec("messaging", "messaging", "amqps", TlsTermination.passthrough),
                createEndpointSpec("messaging-wss", "messaging", "https", TlsTermination.reencrypt)));

        builder.withCertificateProviderTypes(createCertificateProviderTypeDescriptions());
        builder.withRouteServicePorts(createRouteServicePortDescriptions("standard"));
        builder.withEndpointExposeTypes(createEndpointExposeTypeDescription());

        List<AddressSpacePlan> filteredAddressSpaceplans = addressSpacePlans.stream()
                .filter(plan -> "standard".equals(plan.getAddressSpaceType()))
                .collect(Collectors.toList());
        builder.withPlans(filteredAddressSpaceplans);

        List<AddressPlan> filteredAddressPlans = addressPlans.stream()
                .filter(plan -> filteredAddressSpaceplans.stream()
                        .filter(aPlan -> aPlan.getAddressPlans().contains(plan.getMetadata().getName()))
                        .count() > 0)
                .collect(Collectors.toList());


        builder.withInfraConfigs(standardInfraConfigs);

        builder.withAddressTypes(Arrays.asList(
                createAddressType(
                        "deadletter",
                        "A deadletter queue.",
                        filteredAddressPlans),
                createAddressType(
                        "anycast",
                        "A direct messaging address type. Messages sent to an anycast address are not " +
                                "stored but forwarded directly to a consumer.",
                        filteredAddressPlans),
                createAddressType(
                        "multicast",
                        "A direct messaging address type. Messages sent to a multicast address are not " +
                                "stored but forwarded directly to multiple consumers.",
                        filteredAddressPlans),
                createAddressType(
                        "queue",
                        "A store-and-forward queue. A queue may be sharded across multiple storage units, " +
                                "in which case message ordering is no longer guaranteed.",
                        filteredAddressPlans),
                createAddressType(
                        "topic",
                        "A topic address for store-and-forward publish-subscribe messaging. Each message published " +
                                "to a topic address is forwarded to all subscribes on that address.",
                        filteredAddressPlans),
                createAddressType(
                        "subscription",
                        "A subscription on a topic",
                        filteredAddressPlans)));

        return builder.build();
    }

    private AddressSpaceType createBrokeredType(List<AddressSpacePlan> addressSpacePlans, Collection<AddressPlan> addressPlans, List<InfraConfig> brokeredInfraConfigs) {
        AddressSpaceTypeBuilder builder = new AddressSpaceTypeBuilder();
        builder.withName("brokered");
        builder.withDescription("A brokered address space consists of a broker combined with a console for managing addresses.");

        builder.withAvailableEndpoints(Arrays.asList(
                createEndpointSpec("messaging", "messaging", "amqps", TlsTermination.passthrough),
                createEndpointSpec("messaging-wss", "messaging", "amqps", TlsTermination.reencrypt)));

        builder.withCertificateProviderTypes(createCertificateProviderTypeDescriptions());
        builder.withRouteServicePorts(createRouteServicePortDescriptions("brokered"));
        builder.withEndpointExposeTypes(createEndpointExposeTypeDescription());

        List<AddressSpacePlan> filteredAddressSpaceplans = addressSpacePlans.stream()
                .filter(plan -> "brokered".equals(plan.getAddressSpaceType()))
                .collect(Collectors.toList());
        builder.withPlans(filteredAddressSpaceplans);

        List<AddressPlan> filteredAddressPlans = addressPlans.stream()
                .filter(plan -> filteredAddressSpaceplans.stream()
                        .filter(aPlan -> aPlan.getAddressPlans().contains(plan.getMetadata().getName()))
                        .count() > 0)
                .collect(Collectors.toList());

        builder.withInfraConfigs(brokeredInfraConfigs);

        builder.withAddressTypes(Arrays.asList(
                createAddressType(
                        "deadletter",
                        "A deadletter queue",
                        filteredAddressPlans),
                createAddressType(
                        "queue",
                        "A queue that supports selectors, message grouping and transactions",
                        filteredAddressPlans),
                createAddressType(
                        "topic",
                    "A topic supports pub-sub semantics. Messages sent to a topic address is forwarded to all subscribes on that address.",
                    filteredAddressPlans)));

        return builder.build();
    }

    private AddressType createAddressType(String name, String description, List<AddressPlan> addressPlans) {
        AddressTypeBuilder builder = new AddressTypeBuilder();
        builder.withPlans(addressPlans.stream()
                .filter(plan -> plan.getAddressType().equals(name))
                .collect(Collectors.toList()));
        builder.withName(name);
        builder.withDescription(description);
        return builder.build();
    }

    private List<RouteServicePortDescription> createRouteServicePortDescriptions(String addressSpaceType) {
        List<RouteServicePortDescription> servicePortDescriptions = new ArrayList<>();

        String displayName = "brokered".equals(addressSpaceType) ? "Messaging (AMQP, CORE, OpenWire, MQTT, STOMP)": "Messaging (AMQP)";
        servicePortDescriptions.add(new RouteServicePortDescriptionBuilder()
                .withName("amqps")
                .withDisplayName(displayName)
                .withDescription(displayName)
                .withRouteTlsTerminations(List.of(TlsTermination.passthrough))
                .build());

        servicePortDescriptions.add(new RouteServicePortDescriptionBuilder()
                .withName("https")
                .withDisplayName("Websocket messaging (AMQP-WS)")
                .withDescription("Websocket messaging (AMQP-WS)")
                .withRouteTlsTerminations(List.of(TlsTermination.passthrough, TlsTermination.reencrypt))
                .build());

        return servicePortDescriptions;
    }

    private List<CertificateProviderTypeDescription> createCertificateProviderTypeDescriptions() {
        List<CertificateProviderTypeDescription> certificateProviders = new ArrayList<>();
        certificateProviders.add(new CertificateProviderTypeDescriptionBuilder()
                .withName("certBundle")
                .withDisplayName("Certificate Bundle")
                .withDescription("User provided TLS certificate bundle.").build());
        certificateProviders.add(new CertificateProviderTypeDescriptionBuilder()
                .withName("selfsigned")
                .withDisplayName("Self-Signed")
                .withDescription("System generates self-signed TLS certificate.").build());
        if (isOpenShift) {
            certificateProviders.add(new CertificateProviderTypeDescriptionBuilder()
                    .withName("openshift")
                    .withDisplayName("OpenShift")
                    .withDescription("OpenShift provides a TLS certificate.").build());
        }
        return certificateProviders;
    }

    private List<EndpointExposeTypeDescription> createEndpointExposeTypeDescription() {
        List<EndpointExposeTypeDescription> endpointExposeTypeDescriptions = new ArrayList<>();

        if (isOpenShift) {
            endpointExposeTypeDescriptions.add(new EndpointExposeTypeDescriptionBuilder()
                    .withName(ExposeType.route)
                    .withDisplayName("OpenShift Route")
                    .withDescription("OpenShift Route")
                    .build());
        }
        endpointExposeTypeDescriptions.add(new EndpointExposeTypeDescriptionBuilder()
                .withName(ExposeType.loadbalancer)
                .withDisplayName("LoadBalancer service")
                .withDescription("LoadBalancer service")
                .build());

        return endpointExposeTypeDescriptions;
    }

    @Override
    public Watch watchSchema(Watcher<Schema> watcher, Duration resyncInterval) {
        List<Watch> watches = new ArrayList<>();
        watches.add(addressSpacePlanApi.watchResources(items -> {
            synchronized (KubeSchemaApi.this) {
                currentAddressSpacePlans = items;
            }
            updateSchema(watcher);
        }, resyncInterval));

        watches.add(addressPlanApi.watchResources(items -> {
            synchronized (KubeSchemaApi.this) {
                currentAddressPlans = items;
            }
            updateSchema(watcher);
        }, resyncInterval));

        watches.add(brokeredInfraConfigApi.watchResources(items -> {
            synchronized (KubeSchemaApi.this) {
                currentBrokeredInfraConfigs = items;
            }
            updateSchema(watcher);
        }, resyncInterval));

        watches.add(standardInfraConfigApi.watchResources(items -> {
            synchronized (KubeSchemaApi.this) {
                currentStandardInfraConfigs = items;
            }
            updateSchema(watcher);
        }, resyncInterval));

        watches.add(authenticationServiceApi.watchResources(items -> {
            synchronized (KubeSchemaApi.this) {
                currentAuthenticationServices = items;
            }
            updateSchema(watcher);
        }, resyncInterval));

        return () -> {
            Exception e = null;
            for (Watch watch : watches) {
                try {
                    watch.close();
                } catch (Exception ex) {
                    e = ex;
                }
            }
            if (e != null) {
                throw e;
            }
        };
    }

    private synchronized void updateSchema(Watcher<Schema> watcher) throws Exception {
        Schema schema = assembleSchema(currentAddressSpacePlans, currentAddressPlans, currentStandardInfraConfigs, currentBrokeredInfraConfigs, currentAuthenticationServices);
        if (schema != null) {
            watcher.onUpdate(Collections.singletonList(schema));
        }
    }

    Schema assembleSchema(List<io.enmasse.admin.model.v1.AddressSpacePlan> addressSpacePlans, List<io.enmasse.admin.model.v1.AddressPlan> addressPlans, List<StandardInfraConfig> standardInfraConfigs, List<BrokeredInfraConfig> brokeredInfraConfigs, List<AuthenticationService> authenticationServices) {

        Set<AddressPlan> validAddressPlans = new HashSet<>();
        Map<String, io.enmasse.admin.model.v1.AddressPlan> addressPlanByName = new HashMap<>();
        Map<String, AddressPlanStatus> addressPlanStatusMap = new HashMap<>();
        Map<String, AddressSpacePlanStatus> addressSpacePlanStatusMap = new HashMap<>();

        for (io.enmasse.admin.model.v1.AddressPlan addressPlan : addressPlans) {
            addressPlanByName.put(addressPlan.getMetadata().getName(), addressPlan);
            addressPlanStatusMap.put(addressPlan.getMetadata().getName(), new AddressPlanStatusBuilder()
                    .withPhase(Phase.Pending)
                    .build());

        }

        for (StandardInfraConfig standardInfraConfig : standardInfraConfigs) {
            if (standardInfraConfig.getSpec() != null) {
                standardInfraConfig.getSpec().setVersion(defaultVersion);
            }
        }

        for (BrokeredInfraConfig brokeredInfraConfig : brokeredInfraConfigs) {
            if (brokeredInfraConfig.getSpec() != null) {
                brokeredInfraConfig.getSpec().setVersion(defaultVersion);
            }
        }

        List<AddressSpacePlan> validAddressSpacePlans = new ArrayList<>();
        for (io.enmasse.admin.model.v1.AddressSpacePlan addressSpacePlan : addressSpacePlans) {
            addressSpacePlanStatusMap.put(addressSpacePlan.getMetadata().getName(), new AddressSpacePlanStatusBuilder()
                    .withPhase(Phase.Pending)
                    .build());

            List<AddressPlan> plansForAddressSpacePlan = new ArrayList<>();
            for (String addressPlanName : addressSpacePlan.getAddressPlans()) {
                io.enmasse.admin.model.v1.AddressPlan addressPlan = null;
                try {
                    addressPlan = addressPlanByName.get(addressPlanName);
                    if (addressPlan == null) {
                        String message = String.format("Error validating address plan %s: unable to find address plan definition", addressPlanName);
                        log.warn(message);
                        throw new SchemaValidationException(message);
                    }
                    validateAddressPlan(addressSpacePlan.getAddressSpaceType(), addressPlan);
                    plansForAddressSpacePlan.add(addressPlan);
                    addressPlanStatusMap.get(addressPlanName).setPhase(Phase.Active);
                } catch (SchemaValidationException e) {
                    if (addressPlan == null) {
                        addressSpacePlanStatusMap.get(addressSpacePlan.getMetadata().getName()).setPhase(Phase.Failed);
                        addressSpacePlanStatusMap.get(addressSpacePlan.getMetadata().getName()).appendMessage(e.getMessage());
                    } else {
                        addressPlanStatusMap.get(addressPlanName).setPhase(Phase.Failed);
                        addressPlanStatusMap.get(addressPlanName).appendMessage(e.getMessage());
                    }
                }
            }

            try {
                if (addressSpacePlan.getAddressSpaceType().equals("brokered")) {
                    validateAddressSpacePlan(addressSpacePlan, plansForAddressSpacePlan, brokeredInfraConfigs.stream().map(t -> t.getMetadata().getName()).collect(Collectors.toList()));
                } else {
                    validateAddressSpacePlan(addressSpacePlan, plansForAddressSpacePlan, standardInfraConfigs.stream().map(t -> t.getMetadata().getName()).collect(Collectors.toList()));
                }
                validAddressSpacePlans.add(addressSpacePlan);
                validAddressPlans.addAll(plansForAddressSpacePlan);
                addressSpacePlanStatusMap.get(addressSpacePlan.getMetadata().getName()).setPhase(Phase.Active);
            } catch (SchemaValidationException e) {
                addressSpacePlanStatusMap.get(addressSpacePlan.getMetadata().getName()).setPhase(Phase.Failed);
                addressSpacePlanStatusMap.get(addressSpacePlan.getMetadata().getName()).appendMessage(e.getMessage());
            }
        }

        // Update status of plans to provide error messages and status
        if (updateStatus) {
            for (io.enmasse.admin.model.v1.AddressSpacePlan addressSpacePlan : addressSpacePlans) {
                AddressSpacePlanStatus newStatus = addressSpacePlanStatusMap.get(addressSpacePlan.getMetadata().getName());
                if (!newStatus.equals(addressSpacePlan.getStatus())) {
                    addressSpacePlan.setStatus(newStatus);
                    addressSpacePlanApi.patch(addressSpacePlan);
                }
            }
            for (io.enmasse.admin.model.v1.AddressPlan addressPlan : addressPlans) {
                AddressPlanStatus newStatus = addressPlanStatusMap.get(addressPlan.getMetadata().getName());
                if (!newStatus.equals(addressPlan.getStatus())) {
                    addressPlan.setStatus(newStatus);
                    addressPlanApi.patch(addressPlan);
                }
            }
        }

        List<AddressSpaceType> types = new ArrayList<>();
        types.add(createBrokeredType(validAddressSpacePlans, validAddressPlans, new ArrayList<>(brokeredInfraConfigs)));
        types.add(createStandardType(validAddressSpacePlans, validAddressPlans, new ArrayList<>(standardInfraConfigs)));
        return new SchemaBuilder()
                .withAddressSpaceTypes(types)
                .withAuthenticationServices(authenticationServices)
                .withCreationTimestamp(formatter.format(clock.instant()))
                .build();
    }
}
