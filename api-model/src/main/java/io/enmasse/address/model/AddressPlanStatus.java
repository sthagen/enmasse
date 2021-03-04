/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.address.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.enmasse.admin.model.AddressPlan;
import io.enmasse.admin.model.v1.AbstractWithAdditionalProperties;
import io.sundr.builder.annotations.Buildable;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressPlanStatus extends AbstractWithAdditionalProperties {
    @NotNull
    private String name;
    @NotNull
    private Integer partitions;

    private Map<String, Double> resources;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Double> getResources() {
        return resources;
    }

    public void setResources(Map<String, Double> resources) {
        this.resources = new HashMap<>(resources);
    }

    public Integer getPartitions() {
        if (partitions != null) {
            return partitions;
        } else {
            return 1;
        }
    }

    public void setPartitions(Integer partitions) {
        this.partitions = partitions;
    }

    @Override
    public String toString() {
        return "AddressPlanStatus{" +
                "name='" + name + '\'' +
                ", resources=" + resources +
                ", partitions=" + partitions +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddressPlanStatus that = (AddressPlanStatus) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(resources, that.resources) &&
                Objects.equals(partitions, that.partitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, resources, partitions);
    }

    public static AddressPlanStatus fromAddressPlan(AddressPlan addressPlan) {
        AddressPlanStatus planStatus = new AddressPlanStatus();
        planStatus.setName(addressPlan.getMetadata().getName());
        planStatus.setResources(addressPlan.getResources());
        planStatus.setPartitions(addressPlan.getPartitions());
        return planStatus;
    }
}
