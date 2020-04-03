/*
 * Copyright 2019-2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.jdbc.config;

import static io.enmasse.iot.registry.jdbc.Profiles.PROFILE_DEVICE_CONNECTION;
import static io.enmasse.iot.registry.jdbc.Profiles.PROFILE_REGISTRY_ADAPTER;
import static io.enmasse.iot.registry.jdbc.Profiles.PROFILE_REGISTRY_MANAGEMENT;
import static org.eclipse.hono.service.metric.MetricsTags.forService;

import org.eclipse.hono.util.Constants;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class MetricsConfiguration {

    /**
     * Customizer for meter registry.
     *
     * @return The new meter registry customizer.
     */
    @Bean
    @Profile({PROFILE_REGISTRY_ADAPTER, PROFILE_REGISTRY_MANAGEMENT})
    public MeterRegistryCustomizer<MeterRegistry> commonTagsRegistry() {
        return r -> r
                .config()
                .commonTags(forService(Constants.SERVICE_NAME_DEVICE_REGISTRY));
    }

    /**
     * Customizer for meter registry.
     *
     * @return The new meter registry customizer.
     */
    @Bean
    @Profile({PROFILE_DEVICE_CONNECTION})
    public MeterRegistryCustomizer<MeterRegistry> commonTagsConnection() {
        return r -> r
                .config()
                .commonTags(forService(Constants.SERVICE_NAME_DEVICE_CONNECTION));
    }

}
