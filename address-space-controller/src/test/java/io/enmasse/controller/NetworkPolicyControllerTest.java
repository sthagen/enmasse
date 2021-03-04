/*
 * Copyright 2016-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.controller;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyIngressRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.AddressSpaceBuilder;
import io.enmasse.address.model.KubeUtil;
import io.enmasse.admin.model.v1.InfraConfig;
import io.enmasse.admin.model.v1.NetworkPolicy;
import io.enmasse.admin.model.v1.NetworkPolicyBuilder;
import io.enmasse.admin.model.v1.StandardInfraConfigBuilder;
import io.enmasse.config.AnnotationKeys;
import io.enmasse.config.LabelKeys;
import io.enmasse.k8s.util.JULInitializingTest;
import io.enmasse.model.CustomResourceDefinitions;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyEgressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyIngressRuleBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.OpenShiftServer;

public class NetworkPolicyControllerTest extends JULInitializingTest {
    private static final ObjectMapper mapper = new ObjectMapper();
    private OpenShiftClient client;

    public OpenShiftServer openShiftServer = new OpenShiftServer(false, true);

    @BeforeAll
    public static void init() {
        CustomResourceDefinitions.registerAll();
    }

    @BeforeEach
    public void setup() {
        openShiftServer.before();
        client = openShiftServer.getOpenshiftClient();
    }

    @AfterEach
    void tearDown() {
        openShiftServer.after();
    }

    @Test
    public void testCreateFromInfraConfig() throws Exception {
        InfraConfig infraConfig = createTestInfra(createTestPolicy("my", "label"));
        AddressSpace addressSpace = createTestSpace(infraConfig, null);

        NetworkPolicyController controller = new NetworkPolicyController(client);
        controller.reconcileAnyState(addressSpace);

        assertEquals(1, client.network().networkPolicies().list().getItems().size());
        io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy networkPolicy = client.network().networkPolicies().withName(KubeUtil.getNetworkPolicyName(addressSpace)).get();
        assertNotNull(networkPolicy);
        assertEquals("enmasse", networkPolicy.getMetadata().getLabels().get(LabelKeys.APP));
        assertEquals("1234", networkPolicy.getMetadata().getLabels().get(LabelKeys.INFRA_UUID));
        assertThat(networkPolicy.getSpec().getPolicyTypes(), hasItem("Ingress"));
        assertThat(networkPolicy.getSpec().getPolicyTypes(), not(hasItem("Egress")));
        assertTrue(networkPolicy.getSpec().getEgress().isEmpty());

        List<NetworkPolicyIngressRule> ingresses = networkPolicy.getSpec().getIngress();
        assertEquals(2, ingresses.size());
        assertEquals("label", ingresses.get(0).getFrom().get(0).getPodSelector().getMatchLabels().get("my"));
        assertEquals("enmasse", ingresses.get(1).getFrom().get(0).getPodSelector().getMatchLabels().get(LabelKeys.APP), "should permit enmasse traffic");
    }

    @Test
    public void testCreateFromAddressSpaceConfig() throws Exception {
        InfraConfig infraConfig = createTestInfra(null);
        AddressSpace addressSpace = createTestSpace(infraConfig, createTestPolicy("my", "label"));

        NetworkPolicyController controller = new NetworkPolicyController(client);
        controller.reconcileAnyState(addressSpace);

        assertEquals(1, client.network().networkPolicies().list().getItems().size());
        io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy networkPolicy = client.network().networkPolicies().withName(KubeUtil.getNetworkPolicyName(addressSpace)).get();
        assertNotNull(networkPolicy);
        assertEquals("enmasse", networkPolicy.getMetadata().getLabels().get(LabelKeys.APP));
        assertEquals("1234", networkPolicy.getMetadata().getLabels().get(LabelKeys.INFRA_UUID));
        assertThat(networkPolicy.getSpec().getPolicyTypes(), hasItem("Ingress"));
        assertThat(networkPolicy.getSpec().getPolicyTypes(), not(hasItem("Egress")));
        assertTrue(networkPolicy.getSpec().getEgress().isEmpty());

        List<NetworkPolicyIngressRule> ingresses = networkPolicy.getSpec().getIngress();
        assertEquals(2, ingresses.size());
        assertEquals("label", ingresses.get(0).getFrom().get(0).getPodSelector().getMatchLabels().get("my"));
        assertEquals("enmasse", ingresses.get(1).getFrom().get(0).getPodSelector().getMatchLabels().get(LabelKeys.APP), "should permit enmasse traffic");
    }

    @Test
    public void testAddressSpaceOverridesInfra() throws Exception {
        InfraConfig infraConfig = createTestInfra(createTestPolicy("my", "label"));
        AddressSpace addressSpace = createTestSpace(infraConfig, createTestPolicy("my", "overridden"));

        NetworkPolicyController controller = new NetworkPolicyController(client);
        controller.reconcileAnyState(addressSpace);

        assertEquals(1, client.network().networkPolicies().list().getItems().size());
        io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy networkPolicy = client.network().networkPolicies().withName(KubeUtil.getNetworkPolicyName(addressSpace)).get();
        assertNotNull(networkPolicy);
        assertEquals("enmasse", networkPolicy.getMetadata().getLabels().get(LabelKeys.APP));
        assertEquals("1234", networkPolicy.getMetadata().getLabels().get(LabelKeys.INFRA_UUID));
        assertEquals("type1", networkPolicy.getMetadata().getLabels().get(LabelKeys.INFRA_TYPE));
        assertThat(networkPolicy.getSpec().getPolicyTypes(), hasItem("Ingress"));
        assertEquals("overridden", networkPolicy.getSpec().getIngress().get(0).getFrom().get(0).getPodSelector().getMatchLabels().get("my"));
    }

    @Test
    public void testUpdatesWhenChanged() throws Exception {
        InfraConfig infraConfig = createTestInfra(null);
        AddressSpace addressSpace = createTestSpace(infraConfig,
                createTestPolicy("my", "label1"));

        NetworkPolicyController controller = new NetworkPolicyController(client);
        controller.reconcileAnyState(addressSpace);

        assertEquals(1, client.network().networkPolicies().list().getItems().size());
        io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy networkPolicy = client.network().networkPolicies().withName(KubeUtil.getNetworkPolicyName(addressSpace)).get();
        assertNotNull(networkPolicy);
        assertThat(networkPolicy.getSpec().getPolicyTypes(), hasItem("Ingress"));
        assertEquals("label1", networkPolicy.getSpec().getIngress().get(0).getFrom().get(0).getPodSelector().getMatchLabels().get("my"));

        addressSpace = createTestSpace(infraConfig, createTestPolicy("my", "label2"));
        controller.reconcileAnyState(addressSpace);

        assertEquals(1, client.network().networkPolicies().list().getItems().size());
        networkPolicy = client.network().networkPolicies().withName(KubeUtil.getNetworkPolicyName(addressSpace)).get();
        assertNotNull(networkPolicy);
        assertThat(networkPolicy.getSpec().getPolicyTypes(), hasItem("Ingress"));
        assertEquals("label2", networkPolicy.getSpec().getIngress().get(0).getFrom().get(0).getPodSelector().getMatchLabels().get("my"));

        addressSpace = createTestSpace(infraConfig, createTestPolicy("my", "label2", "other", "label3"));
        controller.reconcileAnyState(addressSpace);
        networkPolicy = client.network().networkPolicies().withName(KubeUtil.getNetworkPolicyName(addressSpace)).get();
        assertNotNull(networkPolicy);
        assertThat(networkPolicy.getSpec().getPolicyTypes(), hasItem("Ingress"));
        assertThat(networkPolicy.getSpec().getPolicyTypes(), hasItem("Egress"));
        assertEquals("label2", networkPolicy.getSpec().getIngress().get(0).getFrom().get(0).getPodSelector().getMatchLabels().get("my"));
        assertEquals("label3", networkPolicy.getSpec().getEgress().get(0).getTo().get(0).getPodSelector().getMatchLabels().get("other"));
    }

    @Test
    public void testDeletesWhenRemoved() throws Exception {
        InfraConfig infraConfig = createTestInfra(null);
        AddressSpace addressSpace = createTestSpace(infraConfig, createTestPolicy("my", "label"));

        NetworkPolicyController controller = new NetworkPolicyController(client);
        controller.reconcileAnyState(addressSpace);

        assertEquals(1, client.network().networkPolicies().list().getItems().size());
        io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy networkPolicy = client.network().networkPolicies().withName(KubeUtil.getNetworkPolicyName(addressSpace)).get();
        assertNotNull(networkPolicy);
        assertThat(networkPolicy.getSpec().getPolicyTypes(), hasItem("Ingress"));
        assertEquals("label", networkPolicy.getSpec().getIngress().get(0).getFrom().get(0).getPodSelector().getMatchLabels().get("my"));

        addressSpace = createTestSpace(infraConfig, null);
        controller.reconcileAnyState(addressSpace);
        assertEquals(0, client.network().networkPolicies().list().getItems().size());
        networkPolicy = client.network().networkPolicies().withName(KubeUtil.getNetworkPolicyName(addressSpace)).get();
        assertNull(networkPolicy);
    }

    private NetworkPolicy createTestPolicy(String labelKey, String labelValue) {
        return new NetworkPolicyBuilder()
                .withIngress(Collections.singletonList(new NetworkPolicyIngressRuleBuilder()
                        .addNewFrom()
                        .withNewPodSelector()
                        .addToMatchLabels(labelKey, labelValue)
                        .endPodSelector()
                        .endFrom()
                        .build()))
                .build();
    }

    private NetworkPolicy createTestPolicy(String ingressLabelKey, String ingressLabelValue, String egressLabelKey, String egressLabelValue) {
        return new NetworkPolicyBuilder()
                .withIngress(Collections.singletonList(new NetworkPolicyIngressRuleBuilder()
                        .addNewFrom()
                        .withNewPodSelector()
                        .addToMatchLabels(ingressLabelKey, ingressLabelValue)
                        .endPodSelector()
                        .endFrom()
                        .build()))
                .withEgress(Collections.singletonList(new NetworkPolicyEgressRuleBuilder()
                        .addNewTo()
                        .withNewPodSelector()
                        .addToMatchLabels(egressLabelKey, egressLabelValue)
                        .endPodSelector()
                        .endTo()
                        .build()))
                .build();
    }

    private InfraConfig createTestInfra(NetworkPolicy networkPolicy) throws JsonProcessingException {
        return new StandardInfraConfigBuilder()
                .withNewMetadata()
                .withName("test")
                .endMetadata()

                .withNewSpec()
                .withNetworkPolicy(networkPolicy)
                .endSpec()
                .build();
    }

    private AddressSpace createTestSpace(InfraConfig infraConfig, NetworkPolicy networkPolicy) throws JsonProcessingException {
        return new AddressSpaceBuilder()

                .withNewMetadata()
                .withName("myspace")
                .withNamespace("ns")
                .addToAnnotations(AnnotationKeys.INFRA_UUID, "1234")
                .addToAnnotations(AnnotationKeys.APPLIED_INFRA_CONFIG, mapper.writeValueAsString(infraConfig))
                .endMetadata()

                .withNewSpec()
                .withType("type1")
                .withPlan("plan1")
                .withNetworkPolicy(networkPolicy)
                .endSpec()

                .build();
    }
}
