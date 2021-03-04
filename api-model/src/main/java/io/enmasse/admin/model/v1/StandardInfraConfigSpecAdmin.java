/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.admin.model.v1;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.BuildableReference;
import io.sundr.builder.annotations.Inline;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonPropertyOrder({"resources", "podTemplate"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardInfraConfigSpecAdmin extends AbstractWithAdditionalProperties {

    private StandardInfraConfigSpecAdminResources resources;
    private PodTemplateSpec podTemplate;

    public void setResources(StandardInfraConfigSpecAdminResources resources) {
        this.resources = resources;
    }

    public StandardInfraConfigSpecAdminResources getResources() {
        return resources;
    }

    public PodTemplateSpec getPodTemplate() {
        return podTemplate;
    }

    public void setPodTemplate(PodTemplateSpec podTemplate) {
        this.podTemplate = podTemplate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StandardInfraConfigSpecAdmin that = (StandardInfraConfigSpecAdmin) o;
        return Objects.equals(resources, that.resources) &&
                Objects.equals(podTemplate, that.podTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resources, podTemplate);
    }

    @Override
    public String toString() {
        return "StandardInfraConfigSpecAdmin{" +
                "resources=" + resources +
                ", podTemplate=" + podTemplate +
                '}';
    }
}
