package org.ihawu.spring.boot.starter

import org.ihawu.spring.boot.starter.configuration.IhawuJacksonObjectMapperConfig
import org.ihawu.spring.boot.starter.configuration.IhawuProperties
import org.ihawu.spring.boot.starter.configuration.IhawuSecurityConfig
import org.ihawu.spring.boot.starter.configuration.IhawuWebConfig
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
 */
@AutoConfiguration
@Import(
    value = [
        IhawuSecurityConfig::class,
        IhawuJacksonObjectMapperConfig::class,
        IhawuWebConfig::class,
        ResourcePolicyConfig::class,
    ],
)
@EnableConfigurationProperties(IhawuProperties::class)
@ConditionalOnProperty(prefix = "ihawu", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class IhawuAutoConfiguration
