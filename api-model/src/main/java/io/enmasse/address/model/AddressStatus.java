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
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.BuildableReference;

/**
 * Represents the status of an address
 */
@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder",
        refs= {@BuildableReference(AbstractWithAdditionalProperties.class)}
        )
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AddressStatus extends AbstractWithAdditionalProperties {

    @JsonProperty("isReady")
    private boolean ready = false;
    private Phase phase = Phase.Pending;
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<String> messages = new ArrayList<>();
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<@Valid BrokerStatus> brokerStatuses = new ArrayList<>();
    private AddressPlanStatus planStatus;
    private SubscriptionStatus subscription;
    private MessageTtl messageTtl;
    private MessageRedelivery messageRedelivery;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<@Valid AddressStatusForwarder> forwarders;

    public AddressStatus() {
    }

    public AddressStatus(boolean ready) {
        this.ready = ready;
    }

    public boolean isReady() {
        return ready;
    }

    public Phase getPhase() {
        return phase;
    }

    public List<BrokerStatus> getBrokerStatuses() {
        return Collections.unmodifiableList(brokerStatuses);
    }

    public AddressStatus setReady(boolean ready) {
        this.ready = ready;
        return this;
    }

    public AddressStatus setPhase(Phase phase) {
        this.phase = phase;
        return this;
    }

    public List<String> getMessages() {
        return messages;
    }

    public AddressStatus appendMessage(String message) {
        this.messages.add(message);
        return this;
    }

    public AddressStatus clearMessages() {
        this.messages.clear();
        return this;
    }

    public AddressStatus setMessages(List<String> messages) {
        this.messages = new ArrayList<>(messages);
        return this;
    }

    public AddressStatus appendBrokerStatus(BrokerStatus brokerStatus) {
        this.brokerStatuses.add(brokerStatus);
        return this;
    }

    public AddressStatus setBrokerStatuses(List<BrokerStatus> brokerStatuses) {
        this.brokerStatuses = new ArrayList<>(brokerStatuses);
        return this;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddressStatus status = (AddressStatus) o;
        return ready == status.ready &&
                phase == status.phase &&
                Objects.equals(messages, status.messages) &&
                Objects.equals(brokerStatuses, status.brokerStatuses) &&
                Objects.equals(planStatus, status.planStatus) &&
                Objects.equals(forwarders, status.forwarders) &&
                Objects.equals(subscription, status.subscription) &&
                Objects.equals(messageTtl, status.messageTtl) &&
                Objects.equals(messageRedelivery, status.messageRedelivery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ready, phase, messages, brokerStatuses, planStatus, forwarders, subscription, messageTtl, messageRedelivery);
    }


    @Override
    public String toString() {
        return new StringBuilder()
                .append("{ready=").append(ready)
                .append(",").append("phase=").append(phase)
                .append(",").append("messages=").append(messages)
                .append(",").append("brokerStatuses=").append(brokerStatuses)
                .append(",").append("planStatus=").append(planStatus)
                .append(",").append("forwarders=").append(forwarders)
                .append(",").append("subscription=").append(subscription)
                .append(",").append("messageTtl=").append(messageTtl)
                .append(",").append("messageRedelivery=").append(messageRedelivery)
                .append("}")
                .toString();
    }

    public void addAllBrokerStatuses(List<BrokerStatus> toAdd) {
        brokerStatuses.addAll(toAdd);
    }

    public AddressPlanStatus getPlanStatus() {
        return planStatus;
    }

    public void setPlanStatus(AddressPlanStatus planStatus) {
        this.planStatus = planStatus;
    }

    public List<AddressStatusForwarder> getForwarders() {
        return forwarders;
    }

    public void setForwarders(List<AddressStatusForwarder> forwarders) {
        if (forwarders == null) {
            this.forwarders = null;
        } else {
            this.forwarders = new ArrayList<>(forwarders);
        }
    }

    public SubscriptionStatus getSubscription() {
        return subscription;
    }

    public void setSubscription(SubscriptionStatus subscription) {
        this.subscription = subscription;
    }

    public MessageTtl getMessageTtl() {
        return messageTtl;
    }

    public void setMessageTtl(MessageTtl messageTtl) {
        this.messageTtl = messageTtl;
    }

    public MessageRedelivery getMessageRedelivery() {
        return messageRedelivery;
    }

    public void setMessageRedelivery(MessageRedelivery messageRedelivery) {
        this.messageRedelivery = messageRedelivery;
    }

}
