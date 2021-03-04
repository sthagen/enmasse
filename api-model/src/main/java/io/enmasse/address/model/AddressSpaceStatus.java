/*
 * Copyright 2017-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.address.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import io.enmasse.admin.model.v1.AbstractWithAdditionalProperties;
import io.enmasse.model.validation.ValidBase64;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.BuildableReference;

/**
 * Represents the status of an address
 */
@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder",
        refs = {@BuildableReference(AbstractWithAdditionalProperties.class)}
        )
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AddressSpaceStatus extends AbstractWithAdditionalProperties {
    @JsonProperty("isReady")
    private boolean ready = false;

    private Phase phase = Phase.Pending;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<@Valid EndpointStatus> endpointStatuses = new ArrayList<>();

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<String> messages = new ArrayList<>();

    @ValidBase64
    private String caCert;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<AddressSpaceStatusConnector> connectors = new ArrayList<>();

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<AddressSpaceStatusRouter> routers = new ArrayList<>();

    public AddressSpaceStatus() {
    }

    public AddressSpaceStatus(boolean ready) {
        this.ready = ready;
    }

    public boolean isReady() {
        return ready;
    }

    public AddressSpaceStatus setReady(boolean ready) {
        this.ready = ready;
        return this;
    }

    public void setCaCert(String caCert) {
        this.caCert = caCert;
    }

    public String getCaCert() {
        return caCert;
    }

    public List<String> getMessages() {
        return messages;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public AddressSpaceStatus appendMessage(String message) {
        this.messages.add(message);
        return this;
    }

    public AddressSpaceStatus clearMessages() {
        this.messages.clear();
        return this;
    }

    public AddressSpaceStatus setMessages(List<String> messages) {
        this.messages = messages;
        return this;
    }

    public List<EndpointStatus> getEndpointStatuses() {
        return Collections.unmodifiableList(endpointStatuses);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddressSpaceStatus status = (AddressSpaceStatus) o;
        return ready == status.ready &&
                phase == status.phase &&
                Objects.equals(caCert, status.caCert) &&
                Objects.equals(endpointStatuses, status.endpointStatuses) &&
                Objects.equals(messages, status.messages) &&
                Objects.equals(connectors, status.connectors) &&
                Objects.equals(routers, status.routers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ready, phase, caCert, endpointStatuses, messages, connectors, routers);
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("{ready=").append(ready)
                .append(",").append("phase=").append(phase)
                .append(",").append("caCert=").append(caCert)
                .append(",").append("endpointStatuses=").append(endpointStatuses)
                .append(",").append("messages=").append(messages)
                .append(",").append("connectors=").append(connectors)
                .append(",").append("routers=").append(routers)
                .append("}")
                .toString();
    }

    public AddressSpaceStatus setEndpointStatuses(List<EndpointStatus> endpointStatuses) {
        this.endpointStatuses = new ArrayList<>(endpointStatuses);
        return this;
    }

    public AddressSpaceStatus appendEndpointStatus(EndpointStatus endpointStatus) {
        endpointStatuses.add(endpointStatus);
        return this;
    }

    public AddressSpaceStatus appendConnectorStatus(AddressSpaceStatusConnector connectorStatus) {
        connectors.add(connectorStatus);
        return this;
    }

    public List<AddressSpaceStatusConnector> getConnectors() {
        return connectors;
    }

    public void setConnectors(List<AddressSpaceStatusConnector> connectors) {
        this.connectors = connectors;
    }

    public List<AddressSpaceStatusRouter> getRouters() {
        return routers;
    }

    public void setRouters(List<AddressSpaceStatusRouter> routers) {
        this.routers = routers;
    }
}
