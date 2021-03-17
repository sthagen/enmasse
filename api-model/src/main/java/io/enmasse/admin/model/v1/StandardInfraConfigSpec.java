/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.admin.model.v1;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.sundr.builder.annotations.Buildable;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonPropertyOrder({"version", "admin", "broker", "router"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardInfraConfigSpec extends AbstractWithAdditionalProperties {

    private String version;
    private NetworkPolicy networkPolicy;
    private StandardInfraConfigSpecAdmin admin;
    private StandardInfraConfigSpecBroker broker;
    private StandardInfraConfigSpecRouter router;
    private Boolean globalDLQ;

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setAdmin(StandardInfraConfigSpecAdmin admin) {
        this.admin = admin;
    }

    public StandardInfraConfigSpecAdmin getAdmin() {
        return admin;
    }

    public void setBroker(StandardInfraConfigSpecBroker broker) {
        this.broker = broker;
    }

    public StandardInfraConfigSpecBroker getBroker() {
        return broker;
    }

    public void setRouter(StandardInfraConfigSpecRouter router) {
        this.router = router;
    }

    public StandardInfraConfigSpecRouter getRouter() {
        return router;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StandardInfraConfigSpec that = (StandardInfraConfigSpec) o;
        return Objects.equals(version, that.version) &&
                Objects.equals(networkPolicy, that.networkPolicy) &&
                Objects.equals(globalDLQ, that.globalDLQ) &&
                Objects.equals(admin, that.admin) &&
                Objects.equals(broker, that.broker) &&
                Objects.equals(router, that.router);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, networkPolicy, globalDLQ, admin, broker, router);
    }

    @Override
    public String toString() {
        return "StandardInfraConfigSpec{" +
                "version='" + version + '\'' +
                ", networkPolicy=" + networkPolicy +
                ", globalDLQ=" + globalDLQ +
                ", admin=" + admin +
                ", broker=" + broker +
                ", router=" + router +
                '}';
    }

    public void setNetworkPolicy(NetworkPolicy networkPolicy) {
        this.networkPolicy = networkPolicy;
    }

    public NetworkPolicy getNetworkPolicy() {
        return networkPolicy;
    }

    public Boolean getGlobalDLQ() {
        return globalDLQ;
    }

    public void setGlobalDLQ(Boolean globalDLQ) {
        this.globalDLQ = globalDLQ;
    }
}
