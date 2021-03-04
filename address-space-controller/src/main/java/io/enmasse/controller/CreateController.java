/*
 * Copyright 2017-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.enmasse.address.model.*;
import io.enmasse.admin.model.AddressPlan;
import io.enmasse.admin.model.AddressSpacePlan;
import io.enmasse.admin.model.v1.InfraConfig;
import io.enmasse.config.AnnotationKeys;
import io.enmasse.controller.common.ControllerKind;
import io.enmasse.controller.common.Kubernetes;
import io.enmasse.k8s.api.AddressApi;
import io.enmasse.k8s.api.AddressSpaceApi;
import io.enmasse.k8s.api.EventLogger;
import io.enmasse.k8s.api.SchemaProvider;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static io.enmasse.controller.common.ControllerReason.AddressSpaceChanged;
import static io.enmasse.controller.common.ControllerReason.AddressSpaceCreated;
import static io.enmasse.controller.common.ControllerReason.AddressSpaceUpgraded;
import static io.enmasse.k8s.api.EventLogger.Type.Normal;

public class CreateController implements Controller {
    private static final Logger log = LoggerFactory.getLogger(CreateController.class.getName());

    private final Kubernetes kubernetes;
    private final SchemaProvider schemaProvider;
    private final InfraResourceFactory infraResourceFactory;
    private final EventLogger eventLogger;
    private final String defaultCertProvider;
    private final String version;
    private final AddressSpaceApi addressSpaceApi;
    private final AuthenticationServiceResolver authenticationServiceResolver;

    public CreateController(Kubernetes kubernetes, SchemaProvider schemaProvider, InfraResourceFactory infraResourceFactory, EventLogger eventLogger, String defaultCertProvider, String version, AddressSpaceApi addressSpaceApi, AuthenticationServiceResolver authenticationServiceResolver) {
        this.kubernetes = kubernetes;
        this.schemaProvider = schemaProvider;
        this.infraResourceFactory = infraResourceFactory;
        this.eventLogger = eventLogger;
        this.defaultCertProvider = defaultCertProvider;
        this.version = version;
        this.addressSpaceApi = addressSpaceApi;
        this.authenticationServiceResolver = authenticationServiceResolver;
    }

    private String getAnnotation(Map<String, String> annotations, String key, String defaultValue) {
        return Optional.ofNullable(annotations)
                .flatMap(m -> Optional.ofNullable(m.get(key)))
                .orElse(defaultValue);
    }

    private List<EndpointSpec> validateEndpoints(AddressSpaceResolver addressSpaceResolver, AddressSpace addressSpace) {
        // Set default endpoints from type
        AddressSpaceType addressSpaceType = addressSpaceResolver.getType(addressSpace.getSpec().getType());
        List<EndpointSpec> defaultEndpoints = new ArrayList<>(addressSpaceType.getAvailableEndpoints());

        defaultEndpoints.removeIf(spec -> "mqtt".equals(spec.getService()));

        if (addressSpace.getSpec().getEndpoints().isEmpty()) {
            return defaultEndpoints;
        } else {
            // Validate endpoints;
            List<EndpointSpec> endpoints = new ArrayList<>(addressSpace.getSpec().getEndpoints());
            Set<String> services = defaultEndpoints.stream()
                    .map(EndpointSpec::getService)
                    .collect(Collectors.toSet());
            Set<String> actualServices = endpoints.stream()
                    .map(EndpointSpec::getService)
                    .collect(Collectors.toSet());

            services.removeAll(actualServices);

            // Add default endpoints not specified by user
            for (String service : services) {
                for (EndpointSpec endpointSpec : defaultEndpoints) {
                    if (service.equals(endpointSpec.getService())) {
                        endpoints.add(endpointSpec);
                    }
                }
            }
            return endpoints;
        }
    }

    @Override
    public AddressSpace reconcileActive(AddressSpace addressSpace) throws Exception {
        Schema schema = schemaProvider.getSchema();
        AddressSpaceResolver addressSpaceResolver = new AddressSpaceResolver(schema);
        AppliedConfig appliedConfig = kubernetes.getAppliedConfig(addressSpace);
        if (!addressSpaceResolver.validate(addressSpace, Optional.ofNullable(appliedConfig).map(AppliedConfig::getAddressSpaceSpec).orElse(null))) {
            return addressSpace;
        }

        List<EndpointSpec> endpoints = validateEndpoints(addressSpaceResolver, addressSpace);

        // Ensure the required certs are set
        List<EndpointSpec> newEndpoints = new ArrayList<>();
        for (EndpointSpec endpoint : endpoints) {
            EndpointSpecBuilder endpointBuilder = new EndpointSpecBuilder(endpoint);

            CertSpecBuilder certSpec = endpoint.getCert() != null ? new CertSpecBuilder(endpoint.getCert()) : new CertSpecBuilder();
            if (certSpec.getProvider() == null) {
                certSpec.withProvider(defaultCertProvider);
            }

            if (certSpec.getSecretName() == null) {
                certSpec.withSecretName(KubeUtil.getExternalCertSecretName(endpoint.getService(), addressSpace));
            }

            endpointBuilder.withCert(certSpec.build());
            newEndpoints.add(endpointBuilder.build());
        }
        addressSpace = new AddressSpaceBuilder(addressSpace)
                .editOrNewSpec()
                .withEndpoints(newEndpoints)
                .endSpec()
                .build();

        AddressSpaceType addressSpaceType = addressSpaceResolver.getType(addressSpace.getSpec().getType());
        AddressSpacePlan addressSpacePlan = addressSpaceResolver.getPlan(addressSpaceType, addressSpace.getSpec().getPlan());

        InfraConfig desiredInfraConfig = getInfraConfig(addressSpace);
        InfraConfig currentInfraConfig = kubernetes.getAppliedInfraConfig(addressSpace);


        // Apply changes to ensure controller logic works as expected
        InfraConfigs.setCurrentInfraConfig(addressSpace, currentInfraConfig);
        AppliedConfig.setCurrentAppliedConfig(addressSpace, appliedConfig);

        AppliedConfig desiredConfig = AppliedConfig.create(addressSpace.getSpec(), authenticationServiceResolver.resolve(addressSpace));

        if (currentInfraConfig == null && !kubernetes.existsAddressSpace(addressSpace)) {
            KubernetesList resourceList = new KubernetesListBuilder()
                    .addAllToItems(infraResourceFactory.createInfraResources(addressSpace, desiredInfraConfig, desiredConfig.getAuthenticationServiceSettings()))
                    .build();
            addAppliedInfraConfigAnnotation(resourceList, desiredInfraConfig);
            addAppliedConfigAnnotation(resourceList, desiredConfig);

            log.info("Creating address space {}", addressSpace);

            kubernetes.create(resourceList);
            eventLogger.log(AddressSpaceCreated, "Created address space", Normal, ControllerKind.AddressSpace, addressSpace.getMetadata().getName());
            InfraConfigs.setCurrentInfraConfig(addressSpace, desiredInfraConfig);
            AppliedConfig.setCurrentAppliedConfig(addressSpace, desiredConfig);
            addressSpace.getStatus().setPhase(Phase.Configuring);
        } else if (currentInfraConfig == null || !currentInfraConfig.equals(desiredInfraConfig)) {

            if (version.equals(desiredInfraConfig.getInfraConfigVersion())) {
                addressSpace.getStatus().setPhase(Phase.Configuring);
                if (checkExceedsQuota(addressSpaceType, addressSpacePlan, addressSpace)) {
                    return addressSpace;
                }
                KubernetesList resourceList = new KubernetesListBuilder()
                        .addAllToItems(infraResourceFactory.createInfraResources(addressSpace, desiredInfraConfig, desiredConfig.getAuthenticationServiceSettings()))
                        .build();
                addAppliedInfraConfigAnnotation(resourceList, desiredInfraConfig);
                addAppliedConfigAnnotation(resourceList, desiredConfig);

                log.info("Upgrading address space {}", addressSpace);

                kubernetes.apply(resourceList,desiredInfraConfig.getUpdatePersistentVolumeClaim());
                eventLogger.log(AddressSpaceUpgraded, "Upgraded address space", Normal, ControllerKind.AddressSpace, addressSpace.getMetadata().getName());
                InfraConfigs.setCurrentInfraConfig(addressSpace, desiredInfraConfig);
                AppliedConfig.setCurrentAppliedConfig(addressSpace, desiredConfig);
            } else {
                log.info("Version of desired config ({}) does not match controller version ({}), skipping upgrade", desiredInfraConfig.getInfraConfigVersion(), version);
            }
        } else if (!desiredConfig.equals(appliedConfig)) {
            addressSpace.getStatus().setPhase(Phase.Configuring);
            if (checkExceedsQuota(addressSpaceType, addressSpacePlan, addressSpace)) {
                return addressSpace;
            }

            KubernetesList resourceList = new KubernetesListBuilder()
                    .addAllToItems(infraResourceFactory.createInfraResources(addressSpace, desiredInfraConfig, desiredConfig.getAuthenticationServiceSettings()))
                    .build();
            addAppliedInfraConfigAnnotation(resourceList, desiredInfraConfig);
            addAppliedConfigAnnotation(resourceList, desiredConfig);

            log.info("Updating address space config {}", addressSpace);

            kubernetes.apply(resourceList, desiredInfraConfig.getUpdatePersistentVolumeClaim());
            eventLogger.log(AddressSpaceChanged, "Changed applied config", Normal, ControllerKind.AddressSpace, addressSpace.getMetadata().getName());
            InfraConfigs.setCurrentInfraConfig(addressSpace, desiredInfraConfig);
            AppliedConfig.setCurrentAppliedConfig(addressSpace, desiredConfig);
        }
        return addressSpace;
    }

    private void addAppliedConfigAnnotation(KubernetesList resourceList, AppliedConfig config) throws JsonProcessingException {
        for (HasMetadata resource : resourceList.getItems()) {
            if (resource instanceof Service) {
                AppliedConfig.setCurrentAppliedConfig(resource, config);
            }
        }
    }

    private boolean checkExceedsQuota(AddressSpaceType addressSpaceType, AddressSpacePlan plan, AddressSpace addressSpace) {
        AddressApi addressApi = addressSpaceApi.withAddressSpace(addressSpace);
        Set<Address> addresses = addressApi.listAddresses(addressSpace.getMetadata().getNamespace()).stream()
                .filter(address -> addressSpace.getMetadata().getName().equals(Address.extractAddressSpace(address)))
                .collect(Collectors.toSet());

        Map<String, Double> quota = new HashMap<>();
        Map<String, Double> usage = new HashMap<>();
        for (Map.Entry<String, Double> allowance : plan.getResourceLimits().entrySet()) {
            quota.put(allowance.getKey(), allowance.getValue());
        }

        AddressResolver addressResolver = new AddressResolver(addressSpaceType);
        for (Address address : addresses) {
            AddressPlan addressPlan = addressResolver.getPlan(address);
            for (Map.Entry<String, Double> resourceRequest : addressPlan.getResources().entrySet()) {
                usage.compute(resourceRequest.getKey(), (s, old) -> {
                    if (old == null) {
                        return resourceRequest.getValue();
                    } else {
                        return old + resourceRequest.getValue();
                    }
                });
                usage.compute("aggregate", (s, old) -> {
                    if (old == null) {
                        return resourceRequest.getValue();
                    } else {
                        return old + resourceRequest.getValue();
                    }
                });
            }
        }

        boolean exceedsQuota = false;
        for (Map.Entry<String, Double> usageEntry : usage.entrySet()) {
            Double quotaValue = quota.get(usageEntry.getKey());
            if (quotaValue != null && usageEntry.getValue() > quotaValue) {
                addressSpace.getStatus().appendMessage(String.format("Unable to apply plan %s to address space %s:%s: quota exceeded for resource %s", plan.getAddressPlans(), addressSpace.getMetadata().getNamespace(), addressSpace.getMetadata().getName(), usageEntry.getKey()));
                exceedsQuota = true;
            }
        }
        return exceedsQuota;
    }

    private void addAppliedInfraConfigAnnotation(KubernetesList resourceList, InfraConfig infraConfig) throws JsonProcessingException {
        for (HasMetadata item : resourceList.getItems()) {
            if (item instanceof StatefulSet || item instanceof Service) {
                InfraConfigs.setCurrentInfraConfig(item, infraConfig);
            }
        }
    }

    private InfraConfig getInfraConfig(AddressSpace addressSpace) {
        AddressSpaceResolver addressSpaceResolver = new AddressSpaceResolver(schemaProvider.getSchema());
        return addressSpaceResolver.getInfraConfig(addressSpace.getSpec().getType(), addressSpace.getSpec().getPlan());
    }

    @Override
    public String toString() {
        return "CreateController";
    }
}
