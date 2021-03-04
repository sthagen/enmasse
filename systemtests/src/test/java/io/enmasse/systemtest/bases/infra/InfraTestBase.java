/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.bases.infra;

import io.enmasse.address.model.AddressSpace;
import io.enmasse.admin.model.v1.AddressPlan;
import io.enmasse.systemtest.UserCredentials;
import io.enmasse.systemtest.bases.ITestBase;
import io.enmasse.systemtest.bases.TestBase;
import io.enmasse.systemtest.executor.ExecutionResultData;
import io.enmasse.systemtest.infra.InfraConfiguration;
import io.enmasse.systemtest.logs.CustomLogger;
import io.enmasse.systemtest.platform.KubeCMDClient;
import io.enmasse.systemtest.time.TimeoutBudget;
import io.enmasse.systemtest.utils.TestUtils;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class InfraTestBase extends TestBase implements ITestBase {

    private static final List<String> resizingStorageProvisioners = Arrays.asList("kubernetes.io/aws-ebs", "kubernetes.io/gce-pd",
            "kubernetes.io/azure-file", "kubernetes.io/azure-disk", "kubernetes.io/glusterfs", "kubernetes.io/cinder",
            "kubernetes.io/portworx-volume", "kubernetes.io/rbd");
    private static Logger log = CustomLogger.getLogger();
    protected AddressPlan exampleAddressPlan;
    protected AddressSpace exampleAddressSpace;
    protected UserCredentials exampleUser = new UserCredentials("test", "test");

    protected void assertBroker(InfraConfiguration brokerConfig) {
        log.info("Checking broker infra");
        List<Pod> brokerPods = TestUtils.listBrokerPods(kubernetes, exampleAddressSpace);
        assertEquals(1, brokerPods.size());

        Pod broker = brokerPods.stream().findFirst().get();

        ResourceRequirements resources = broker.getSpec().getContainers().stream()
                .filter(container -> container.getName().equals("broker"))
                .findFirst()
                .map(Container::getResources)
                .get();
        assertEquals(new Quantity(brokerConfig.getMemory()), resources.getLimits().get("memory"),
                "Broker memory limit incorrect");
        assertEquals(new Quantity(brokerConfig.getMemory()), resources.getRequests().get("memory"),
                "Broker memory requests incorrect");
        if (brokerConfig.getCpu() != null) {
            assertEquals(new Quantity(brokerConfig.getCpu()), resources.getLimits().get("cpu"),
                    "Broker cpu limit incorrect");
            assertEquals(new Quantity(brokerConfig.getCpu()), resources.getRequests().get("cpu"),
                    "Broker cpu requests incorrect");
        }

        if (brokerConfig.getBrokerStorage() != null) {
            PersistentVolumeClaim brokerVolumeClaim = getBrokerPVCData(broker);
            assertEquals(new Quantity(brokerConfig.getBrokerStorage()), brokerVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                    "Broker data storage request incorrect");
        }

        if (brokerConfig.getBrokerJavaOpts() != null) {
            brokerPods.forEach(pod -> {
                ExecutionResultData result = KubeCMDClient.runOnCluster("exec", pod.getMetadata().getName(), "-n",
                        pod.getMetadata().getNamespace(), "ps", "auxww");
                assertTrue(result.getRetCode(), result.getStdOut());
                assertTrue(result.getStdOut().contains(brokerConfig.getBrokerJavaOpts()),
                        "Unable to find expected java opts in process argument list: " + result.getStdOut());
            });
        }

        if (brokerConfig.getTemplateSpec() != null) {
            assertTemplateSpec(broker, brokerConfig.getTemplateSpec());
        }
    }

    protected void assertTemplateSpec(Pod pod, PodTemplateSpec templateSpec) {
        if (templateSpec.getMetadata().getLabels() != null) {
            for (Map.Entry<String, String> labelPair : templateSpec.getMetadata().getLabels().entrySet()) {
                assertEquals(labelPair.getValue(), pod.getMetadata().getLabels().get(labelPair.getKey()), "Labels do not match");
            }
        }

        if (templateSpec.getSpec().getAffinity() != null) {
            assertEquals(templateSpec.getSpec().getAffinity(), pod.getSpec().getAffinity(), "Affinity rules do not match");
        }

        if (templateSpec.getSpec().getPriorityClassName() != null) {
            assertEquals(templateSpec.getSpec().getPriorityClassName(), pod.getSpec().getPriorityClassName(), "Priority class names do not match");
        }

        if (templateSpec.getSpec().getTolerations() != null) {
            for (Toleration expected : templateSpec.getSpec().getTolerations()) {
                boolean found = false;
                for (Toleration actual : pod.getSpec().getTolerations()) {
                    if (actual.equals(expected)) {
                        found = true;
                        break;
                    }
                }
                assertTrue(found, "Did not find expected toleration " + expected);
            }
        }

        for (Container expectedContainer : templateSpec.getSpec().getContainers()) {
            for (Container actualContainer : pod.getSpec().getContainers()) {
                if (expectedContainer.getName().equals(actualContainer.getName())) {
                    assertEquals(expectedContainer.getResources(), actualContainer.getResources());
                }
            }
        }
    }

    protected void assertAdminConsole(InfraConfiguration adminConfig) {
        log.info("Checking admin console infra");
        List<Pod> adminPods = TestUtils.listAdminConsolePods(kubernetes, exampleAddressSpace);
        assertEquals(1, adminPods.size());

        List<ResourceRequirements> adminResources = adminPods.stream().findFirst().get().getSpec().getContainers()
                .stream().map(Container::getResources).collect(Collectors.toList());

        for (ResourceRequirements requirements : adminResources) {
            assertEquals(new Quantity(adminConfig.getMemory()), requirements.getLimits().get("memory"),
                    "Admin console memory limit incorrect");
            assertEquals(new Quantity(adminConfig.getMemory()), requirements.getRequests().get("memory"),
                    "Admin console memory requests incorrect");
            if (adminConfig.getCpu() != null) {
                assertEquals(new Quantity(adminConfig.getCpu()), requirements.getLimits().get("cpu"),
                        "Admin console cpu limit incorrect");
                assertEquals(new Quantity(adminConfig.getCpu()), requirements.getRequests().get("cpu"),
                        "Admin console cpu requests incorrect");
            }
        }

        if (adminConfig.getTemplateSpec() != null) {
            assertTemplateSpec(adminPods.get(0), adminConfig.getTemplateSpec());
        }
    }

    protected void waitUntilInfraReady(Supplier<Boolean> assertCall, TimeoutBudget timeout) throws InterruptedException {
        log.info("Start waiting for infra ready");
        AssertionFailedError lastException = null;
        while (!timeout.timeoutExpired()) {
            try {
                assertCall.get();
                log.info("assert infra ready succeed");
                return;
            } catch (AssertionFailedError e) {
                lastException = e;
            }
            log.debug("next iteration, remaining time: {}", timeout.timeLeft());
            Thread.sleep(5000);
        }
        log.error("Timeout assert infra expired");
        if (lastException != null) {
            throw lastException;
        }
    }

    protected PersistentVolumeClaim getBrokerPVCData(Pod broker) {
        String brokerVolumeClaimName = broker.getSpec().getVolumes().stream()
                .filter(volume -> volume.getName().equals("data"))
                .findFirst().get()
                .getPersistentVolumeClaim().getClaimName();
        return TestUtils.listPersistentVolumeClaims(kubernetes, exampleAddressSpace).stream()
                .filter(pvc -> pvc.getMetadata().getName().equals(brokerVolumeClaimName))
                .findFirst().get();
    }

    protected Boolean volumeResizingSupported() throws Exception {
        List<Pod> brokerPods = TestUtils.listBrokerPods(kubernetes, exampleAddressSpace);
        assertEquals(1, brokerPods.size());
        Pod broker = brokerPods.stream().findFirst().get();
        PersistentVolumeClaim brokerVolumeClaim = getBrokerPVCData(broker);
        String brokerStorageClassName = brokerVolumeClaim.getSpec().getStorageClassName();
        if (brokerStorageClassName != null) {
            StorageClass brokerStorageClass = kubernetes.getStorageClass(brokerStorageClassName);
            if (resizingStorageProvisioners.contains(brokerStorageClass.getProvisioner())) {
                if (brokerStorageClass.getAllowVolumeExpansion() != null && brokerStorageClass.getAllowVolumeExpansion()) {
                    log.info("Testing broker volume resize because of {}:{}", brokerStorageClassName, brokerStorageClass.getProvisioner());
                    return true;
                } else {
                    log.info("Skipping broker volume resize due to allowVolumeExpansion in StorageClass {} disabled", brokerStorageClassName);
                }
            } else {
                log.info("Skipping broker volume resize due to provisioner: {}", brokerStorageClass.getProvisioner());
            }
        } else {
            log.info("Skipping broker volume resize due to missing StorageClass name in PVC {}", brokerVolumeClaim.getMetadata().getName());
        }
        return false;
    }

}
