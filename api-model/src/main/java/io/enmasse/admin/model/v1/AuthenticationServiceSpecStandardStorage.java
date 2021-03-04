/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.admin.model.v1;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.fabric8.kubernetes.api.model.Quantity;
import io.sundr.builder.annotations.Buildable;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonPropertyOrder({"type", "size"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthenticationServiceSpecStandardStorage extends AbstractWithAdditionalProperties {
    private AuthenticationServiceSpecStandardType type;
    private Quantity size;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthenticationServiceSpecStandardStorage that = (AuthenticationServiceSpecStandardStorage) o;
        return type == that.type && Objects.equals(size, that.size);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, size);
    }

    @Override
    public String toString() {
        return "AuthenticationServiceSpecStandardStorage{" +
                "type=" + type +
                ", size=" + size +
                '}';
    }

    public AuthenticationServiceSpecStandardType getType() {
        return type;
    }

    public void setType(AuthenticationServiceSpecStandardType type) {
        this.type = type;
    }

    public Quantity getSize() {
        return size;
    }

    public void setSize(Quantity size) {
        this.size = size;
    }
}
