/*
 * Copyright 2017-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.address.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.MoreObjects.ToStringHelper;

import io.enmasse.admin.model.v1.AbstractWithAdditionalProperties;
import io.sundr.builder.annotations.Buildable;

import java.util.Objects;

/**
 * Represents an authentication service for an {@link AddressSpace}.
 */
@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
        )
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthenticationService extends AbstractWithAdditionalProperties {

    private String name;

    private AuthenticationServiceSettings overrides;

    // TODO: Keep for backwards compatibility
    private AuthenticationServiceType type;

    public AuthenticationService() {
    }

    public void setType(AuthenticationServiceType type) {
        this.type = type;
    }

    public AuthenticationServiceType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOverrides(AuthenticationServiceSettings overrides) {
        this.overrides = overrides;
    }

    public AuthenticationServiceSettings getOverrides() {
        return overrides;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final AuthenticationService that = (AuthenticationService) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(type, that.type) &&
                Objects.equals(overrides, that.overrides);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, overrides);
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("name", this.name)
                .add("type", this.type)
                .add("overrides", this.overrides);
    }
}
