/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.controller;

import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.Phase;
import io.enmasse.address.model.AddressSpaceStatusConnector;
import io.enmasse.metrics.api.MetricLabel;
import io.enmasse.metrics.api.MetricType;
import io.enmasse.metrics.api.MetricValue;
import io.enmasse.metrics.api.Metrics;
import io.enmasse.metrics.api.ScalarMetric;

import java.util.*;

import static io.enmasse.address.model.Phase.Active;
import static io.enmasse.address.model.Phase.Configuring;
import static io.enmasse.address.model.Phase.Failed;
import static io.enmasse.address.model.Phase.Pending;
import static io.enmasse.address.model.Phase.Terminating;

public class MetricsReporterController implements Controller {
    private final String version;
    private volatile List<MetricValue> readyValues = new ArrayList<>();
    private volatile List<MetricValue> notReadyValues = new ArrayList<>();
    private volatile List<MetricValue> readyConnectorValues = new ArrayList<>();
    private volatile List<MetricValue> notReadyConnectorValues = new ArrayList<>();
    private volatile List<MetricValue> numConnectors = new ArrayList<>();
    private volatile int numAddressSpaces = 0;
    private volatile Map<Phase, Long> countByPhase = new HashMap<>();

    public MetricsReporterController(Metrics metrics, String version) {
        this.version = version;
        registerMetrics(metrics);
    }

    public void reconcileAll(List<AddressSpace> addressSpaces) throws Exception {
        List<MetricValue> readyValues = new ArrayList<>();
        List<MetricValue> notReadyValues = new ArrayList<>();
        List<MetricValue> readyConnectorValues = new ArrayList<>();
        List<MetricValue> notReadyConnectorValues = new ArrayList<>();
        List<MetricValue> numConnectors = new ArrayList<>();

        for (Phase phase : Phase.values()) {
            countByPhase.put(phase, 0L);
        }

        for (AddressSpace addressSpace : addressSpaces) {
            MetricLabel[] labels = new MetricLabel[]{new MetricLabel("name", addressSpace.getMetadata().getName()), new MetricLabel("namespace", addressSpace.getMetadata().getNamespace())};
            readyValues.add(new MetricValue(addressSpace.getStatus().isReady() ? 1 : 0, labels));
            notReadyValues.add(new MetricValue(addressSpace.getStatus().isReady() ? 0 : 1, labels));
            numConnectors.add(new MetricValue(addressSpace.getStatus().getConnectors().size(), labels));
            countByPhase.put(addressSpace.getStatus().getPhase(), 1 + countByPhase.get(addressSpace.getStatus().getPhase()));

            for (AddressSpaceStatusConnector connectorStatus : addressSpace.getStatus().getConnectors()) {

                MetricLabel[] connectorLabels = new MetricLabel[]{new MetricLabel("name", addressSpace.getMetadata().getName()), new MetricLabel("namespace", addressSpace.getMetadata().getNamespace())};
                readyConnectorValues.add(new MetricValue(connectorStatus.isReady() ? 1 : 0, connectorLabels));
                notReadyConnectorValues.add(new MetricValue(connectorStatus.isReady() ? 0 : 1, connectorLabels));
            }
        }

        this.readyValues = readyValues;
        this.notReadyValues = notReadyValues;
        this.readyConnectorValues = readyConnectorValues;
        this.notReadyConnectorValues = notReadyConnectorValues;
        this.numConnectors = numConnectors;
        this.numAddressSpaces = addressSpaces.size();
    }

    private void registerMetrics(Metrics metrics) {
        metrics.registerMetric(new ScalarMetric(
                "version",
                "The version of the address-space-controller",
                MetricType.gauge,
                () -> Collections.singletonList(new MetricValue(0, new MetricLabel("name", "address-space-controller"), new MetricLabel("version", version)))));

        metrics.registerMetric(new ScalarMetric(
                "address_space_status_ready",
                "Describes whether the address space is in a ready state",
                MetricType.gauge,
                () -> readyValues));

        metrics.registerMetric(new ScalarMetric(
                "address_space_status_not_ready",
                "Describes whether the address space is in a not_ready state",
                MetricType.gauge,
                () -> notReadyValues));

        metrics.registerMetric(new ScalarMetric(
                "address_spaces_total",
                "Total number of address spaces",
                MetricType.gauge,
                () -> Collections.singletonList(new MetricValue(numAddressSpaces))));

        metrics.registerMetric(new ScalarMetric(
                "address_space_connector_status_ready",
                "Describes whether the connector in an address space is in a ready state",
                MetricType.gauge,
                () -> readyConnectorValues));

        metrics.registerMetric(new ScalarMetric(
                "address_space_connector_status_not_ready",
                "Describes whether the connector in an address space is in a not_ready state",
                MetricType.gauge,
                () -> notReadyConnectorValues));

        metrics.registerMetric(new ScalarMetric(
                "address_space_connectors_total",
                "Total number of connectors of address spaces",
                MetricType.gauge,
                () -> numConnectors));

        metrics.registerMetric(new ScalarMetric(
                "address_spaces_pending_total",
                "Total number of address spaces in Pending state",
                MetricType.gauge,
                () -> Collections.singletonList(new MetricValue(countByPhase.get(Pending)))));

        metrics.registerMetric(new ScalarMetric(
                "address_spaces_failed_total",
                "Total number of address spaces in Failed state",
                MetricType.gauge,
                () -> Collections.singletonList(new MetricValue(countByPhase.get(Failed)))));

        metrics.registerMetric(new ScalarMetric(
                "address_spaces_terminating_total",
                "Total number of address spaces in Terminating state",
                MetricType.gauge,
                () -> Collections.singletonList(new MetricValue(countByPhase.get(Terminating)))));

        metrics.registerMetric(new ScalarMetric(
                "address_spaces_configuring_total",
                "Total number of address spaces in Configuring state",
                MetricType.gauge,
                () -> Collections.singletonList(new MetricValue(countByPhase.get(Configuring)))));

        metrics.registerMetric(new ScalarMetric(
                "address_spaces_active_total",
                "Total number of address spaces in Active state",
                MetricType.gauge,
                () -> Collections.singletonList(new MetricValue(countByPhase.get(Active)))));
    }

    @Override
    public String toString() {
        return "MetricsReporterController";
    }
}