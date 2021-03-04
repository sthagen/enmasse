/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.address.model;

import io.enmasse.admin.model.v1.AbstractWithAdditionalProperties;
import io.sundr.builder.annotations.Buildable;

import java.util.Objects;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
public class ExportSpec extends AbstractWithAdditionalProperties {
    private ExportKind kind;
    private String name;

    public ExportKind getKind() {
        return kind;
    }

    public void setKind(ExportKind kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "ExportSpec{" +
                "kind=" + kind +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExportSpec that = (ExportSpec) o;
        return kind == that.kind &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name);
    }
}
