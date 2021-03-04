/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.address.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.MoreObjects.ToStringHelper;

import io.enmasse.admin.model.v1.AbstractWithAdditionalProperties;
import io.fabric8.kubernetes.api.model.SecretReference;
import io.sundr.builder.annotations.Buildable;

import java.util.Objects;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthenticationServiceSettings extends AbstractWithAdditionalProperties {
    private String host;
    private Integer port;
    private String realm;
    private SecretReference caCertSecret;
    private SecretReference clientCertSecret;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public SecretReference getCaCertSecret() {
        return caCertSecret;
    }

    public void setCaCertSecret(SecretReference caCertSecret) {
        this.caCertSecret = caCertSecret;
    }

    public SecretReference getClientCertSecret() {
        return clientCertSecret;
    }

    public void setClientCertSecret(SecretReference clientCertSecret) {
        this.clientCertSecret = clientCertSecret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthenticationServiceSettings that = (AuthenticationServiceSettings) o;
        return Objects.equals(host, that.host) &&
                Objects.equals(port, that.port) &&
                Objects.equals(realm, that.realm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, realm);
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("host", this.host)
                .add("port", this.port)
                .add("realm", this.realm)
                .add("caCertSecert", this.caCertSecret)
                .add("clientCertSecret", this.clientCertSecret);
    }

}
