package org.ihawu.spring.boot.starter

import org.ihawu.spring.boot.starter.configuration.IhawuJacksonObjectMapperConfig
import org.ihawu.spring.boot.starter.configuration.IhawuObservabilityConfig
import org.ihawu.spring.boot.starter.configuration.IhawuProperties
import org.ihawu.spring.boot.starter.configuration.IhawuSecurityConfig
import org.ihawu.spring.boot.starter.configuration.IhawuWebConfig
import org.ihawu.spring.boot.starter.configuration.MaskingContractValidationConfig
import org.ihawu.spring.boot.starter.configuration.ResourcePolicyConfig
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import

/**
 * Autoconfiguration entry point for the Ihawu starter.
 * Registers [org.ihawu.spring.boot.starter.configuration.IhawuProperties] and contributes Ihawu's beans, backing off when `ihawu.enabled=false`.
 * Each bean is guarded so applications can override it.
 *
 * Ordered after the metrics auto-configs (via `afterName`, string form so there is no compile
 * dependency on actuator) so [org.ihawu.spring.boot.starter.configuration.IhawuObservabilityConfig]'s
 * `@ConditionalOnBean(MeterRegistry)` sees the registry — otherwise the Micrometer failure sink would
 * silently never register (a `@ConditionalOnBean` ordering pitfall).
 */
@AutoConfiguration(
    afterName = [
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration",
    ],
)
@Import(
    value = [
        IhawuSecurityConfig::class,
        IhawuJacksonObjectMapperConfig::class,
        IhawuObservabilityConfig::class,
        IhawuWebConfig::class,
        ResourcePolicyConfig::class,
        MaskingContractValidationConfig::class,
    ],
)
@EnableConfigurationProperties(IhawuProperties::class)
@ConditionalOnProperty(prefix = "ihawu", name = ["enabled"], havingValue = "true", matchIfMissing = true)
internal class IhawuAutoConfiguration
