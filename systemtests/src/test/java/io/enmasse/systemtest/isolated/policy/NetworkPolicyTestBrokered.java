/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.isolated.policy;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressBuilder;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.AddressSpaceBuilder;
import io.enmasse.admin.model.v1.AddressPlan;
import io.enmasse.admin.model.v1.AddressSpacePlan;
import io.enmasse.admin.model.v1.AddressSpacePlanBuilder;
import io.enmasse.admin.model.v1.BrokeredInfraConfig;
import io.enmasse.admin.model.v1.BrokeredInfraConfigBuilder;
import io.enmasse.admin.model.v1.BrokeredInfraConfigSpecAdminBuilder;
import io.enmasse.admin.model.v1.BrokeredInfraConfigSpecBrokerBuilder;
import io.enmasse.admin.model.v1.ResourceAllowance;
import io.enmasse.admin.model.v1.ResourceRequest;
import io.enmasse.systemtest.Environment;
import io.enmasse.systemtest.UserCredentials;
import io.enmasse.systemtest.bases.TestBase;
import io.enmasse.systemtest.bases.isolated.ITestIsolatedBrokered;
import io.enmasse.systemtest.clients.ClientUtils;
import io.enmasse.systemtest.condition.OpenShift;
import io.enmasse.systemtest.condition.OpenShiftVersion;
import io.enmasse.systemtest.messagingclients.ExternalMessagingClient;
import io.enmasse.systemtest.messagingclients.rhea.RheaClientReceiver;
import io.enmasse.systemtest.messagingclients.rhea.RheaClientSender;
import io.enmasse.systemtest.model.address.AddressType;
import io.enmasse.systemtest.model.addressspace.AddressSpaceType;
import io.enmasse.systemtest.platform.Kubernetes;
import io.enmasse.systemtest.platform.apps.SystemtestsKubernetesApps;
import io.enmasse.systemtest.utils.AddressUtils;
import io.enmasse.systemtest.utils.PlanUtils;
import io.enmasse.systemtest.utils.TestUtils;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyIngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyPeer;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyPeerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OpenShift(version = OpenShiftVersion.OCP4)
class NetworkPolicyTestBrokered extends TestBase implements ITestIsolatedBrokered {
    private UserCredentials credentials = new UserCredentials("test", "test");
    private String blockedSpace = "blocked-namespace";
    private String allowedSpace = "allowed-namespace";

    @AfterEach
    void clearNamespaces() throws Exception {
        kubernetes.deleteNamespace(blockedSpace);
        TestUtils.waitForNamespaceDeleted(kubernetes, blockedSpace);
        kubernetes.deleteNamespace(allowedSpace);
        TestUtils.waitForNamespaceDeleted(kubernetes, allowedSpace);
    }

    @Test
    void testNetworkPolicyWithPodSelector() throws Exception {
        int expectedMsgCount = 5;

        LabelSelector namespace = new LabelSelectorBuilder()
                .withMatchLabels(Collections.singletonMap("allowed", "true"))
                .build();
        LabelSelector pod = new LabelSelectorBuilder()
                .withMatchLabels(Map.of("app", allowedSpace))
                .build();
        NetworkPolicyPeer networkPolicyPeer = new NetworkPolicyPeerBuilder()
                .withPodSelector(pod)
                .withNamespaceSelector(namespace)
                .build();

        BrokeredInfraConfig standardInfraConfig = prepareConfig(networkPolicyPeer);
        AddressPlan addressPlan = prepareAddressPlan();
        AddressSpacePlan addressSpacePlan = prepareAddressSpacePlan(standardInfraConfig, addressPlan);
        AddressSpace addressSpace = prepareAddressSpace(addressSpacePlan);
        Address dest = prepareAddress(addressSpace, addressPlan);

        SystemtestsKubernetesApps.deployMessagingClientApp(blockedSpace);
        SystemtestsKubernetesApps.deployMessagingClientApp(allowedSpace);

        ExternalMessagingClient allowedClientSender = ClientUtils.getPolicyClient(new RheaClientSender(allowedSpace), dest, addressSpace);
        ExternalMessagingClient allowedClientReceiver = ClientUtils.getPolicyClient(new RheaClientReceiver(allowedSpace), dest, addressSpace);

        assertTrue(allowedClientSender.run(), "Sender failed, expected return code 0");
        assertTrue(allowedClientReceiver.run(), "Receiver failed, expected return code 0");

        assertEquals(expectedMsgCount, allowedClientSender.getMessages().size(),
                String.format("Expected %d sent messages", expectedMsgCount));
        assertEquals(expectedMsgCount, allowedClientReceiver.getMessages().size(),
                String.format("Expected %d received messages", expectedMsgCount));

        ExternalMessagingClient blockedClientSender = ClientUtils.getPolicyClient(new RheaClientSender(blockedSpace), dest, addressSpace);
        ExternalMessagingClient blockedClientReceiver = ClientUtils.getPolicyClient(new RheaClientReceiver(blockedSpace), dest, addressSpace);

        assertFalse(blockedClientSender.run(10), "Sender was successful, expected return code -1");
        assertFalse(blockedClientReceiver.run(10), "Receiver was successful, expected return code -1");

    }

    @Test
    void testNetworkPolicyWithNamespaceSelector() throws Exception {
        int expectedMsgCount = 5;

        LabelSelector namespace = new LabelSelectorBuilder()
                .withMatchLabels(Collections.singletonMap("allowed", "true"))
                .build();
        NetworkPolicyPeer networkPolicyPeer = new NetworkPolicyPeerBuilder()
                .withNamespaceSelector(namespace)
                .build();

        BrokeredInfraConfig standardInfraConfig = prepareConfig(networkPolicyPeer);
        AddressPlan addressPlan = prepareAddressPlan();
        AddressSpacePlan addressSpacePlan = prepareAddressSpacePlan(standardInfraConfig, addressPlan);
        AddressSpace addressSpace = prepareAddressSpace(addressSpacePlan);
        Address dest = prepareAddress(addressSpace, addressPlan);

        SystemtestsKubernetesApps.deployMessagingClientApp(blockedSpace);
        SystemtestsKubernetesApps.deployMessagingClientApp(allowedSpace);

        ExternalMessagingClient allowedClientSender = ClientUtils.getPolicyClient(new RheaClientSender(allowedSpace), dest, addressSpace);
        ExternalMessagingClient allowedClientReceiver = ClientUtils.getPolicyClient(new RheaClientReceiver(allowedSpace), dest, addressSpace);

        assertTrue(allowedClientSender.run(), "Sender failed, expected return code 0");
        assertTrue(allowedClientReceiver.run(), "Receiver failed, expected return code 0");

        assertEquals(expectedMsgCount, allowedClientSender.getMessages().size(),
                String.format("Expected %d sent messages", expectedMsgCount));
        assertEquals(expectedMsgCount, allowedClientReceiver.getMessages().size(),
                String.format("Expected %d received messages", expectedMsgCount));

        ExternalMessagingClient blockedClientSender = ClientUtils.getPolicyClient(new RheaClientSender(blockedSpace), dest, addressSpace);
        ExternalMessagingClient blockedClientReceiver = ClientUtils.getPolicyClient(new RheaClientReceiver(blockedSpace), dest, addressSpace);

        assertFalse(blockedClientSender.run(10), "Sender was successful, expected return code -1");
        assertFalse(blockedClientReceiver.run(10), "Receiver was successful, expected return code -1");

    }

    private BrokeredInfraConfig prepareConfig(NetworkPolicyPeer networkPolicyPeer) {

        PodTemplateSpec brokerTemplateSpec = PlanUtils.createTemplateSpec(Collections.singletonMap("mycomponent", "broker"), "mybrokernode", "broker");
        PodTemplateSpec adminTemplateSpec = PlanUtils.createTemplateSpec(Collections.singletonMap("mycomponent", "admin"), "myadminnode", "admin");
        BrokeredInfraConfig brokeredInfraConfig = new BrokeredInfraConfigBuilder()
                .withNewMetadata()
                .withName("test-infra-3-brokered")
                .endMetadata()
                .withNewSpec()
                .withNewNetworkPolicy()
                .withIngress(
                        new NetworkPolicyIngressRuleBuilder()
                                .withFrom(networkPolicyPeer)
                                .withPorts()
                                .build()
                )
                .endNetworkPolicy()
                .withVersion(Environment.getInstance().enmasseVersion())
                .withBroker(new BrokeredInfraConfigSpecBrokerBuilder()
                        .withAddressFullPolicy("FAIL")
                        .withNewResources()
                        .withMemory("512Mi")
                        .withStorage("1Gi")
                        .endResources()
                        .withPodTemplate(brokerTemplateSpec)
                        .build())
                .withAdmin(new BrokeredInfraConfigSpecAdminBuilder()
                        .withNewResources()
                        .withMemory("512Mi")
                        .endResources()
                        .withPodTemplate(adminTemplateSpec)
                        .build())
                .endSpec()
                .build();
        isolatedResourcesManager.createInfraConfig(brokeredInfraConfig);
        return brokeredInfraConfig;
    }

    private AddressSpacePlan prepareAddressSpacePlan(BrokeredInfraConfig brokeredInfraConfig, AddressPlan addressPlan) throws Exception {
        AddressSpacePlan exampleSpacePlan = new AddressSpacePlanBuilder()
                .withNewMetadata()
                .withName("example-space-plan-brokered")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withAddressSpaceType(AddressSpaceType.BROKERED.toString())
                .withShortDescription("Custom systemtests defined address space plan")
                .withInfraConfigRef(brokeredInfraConfig.getMetadata().getName())
                .withResourceLimits(Stream.of(new ResourceAllowance("broker", 3.0)).collect(Collectors.toMap(ResourceAllowance::getName, ResourceAllowance::getMax)))
                .withAddressPlans(Stream.of(addressPlan).map(addressPlan1 -> addressPlan1.getMetadata().getName()).collect(Collectors.toList()))
                .endSpec()
                .build();
        isolatedResourcesManager.createAddressSpacePlan(exampleSpacePlan);
        return exampleSpacePlan;
    }

    private AddressPlan prepareAddressPlan() throws Exception {
        AddressPlan exampleAddressPlan = PlanUtils.createAddressPlanObject("example-queue-plan-brokered", AddressType.QUEUE,
                Arrays.asList(new ResourceRequest("broker", 1.0), new ResourceRequest("router", 1.0)));

        isolatedResourcesManager.createAddressPlan(exampleAddressPlan);
        return exampleAddressPlan;
    }

    private AddressSpace prepareAddressSpace(AddressSpacePlan addressSpacePlan) throws Exception {
        AddressSpace exampleAddressSpace = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("brokered-address-space")
                .withNamespace(Kubernetes.getInstance().getInfraNamespace())
                .withLabels(Collections.singletonMap("allowed", "true"))
                .endMetadata()
                .withNewSpec()
                .withType(getAddressSpaceType().toString())
                .withPlan(addressSpacePlan.getMetadata().getName())
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();

        isolatedResourcesManager.createAddressSpace(exampleAddressSpace);
        isolatedResourcesManager.createOrUpdateUser(exampleAddressSpace, credentials);
        return exampleAddressSpace;
    }

    private Address prepareAddress(AddressSpace addressSpace, AddressPlan addressPlan) throws Exception {
        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(addressSpace.getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(addressSpace, "message-basic-policy"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("messageBasicPolicy")
                .withPlan(addressPlan.getMetadata().getName())
                .endSpec()
                .build();

        isolatedResourcesManager.setAddresses(dest);
        return dest;
    }
}
