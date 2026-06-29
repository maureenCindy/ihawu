package com.ihawu.spring.boot.starter

import com.ihawu.spring.boot.starter.security.IhawuSecurityConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import

/**
 * Autoconfiguration entry point for the Ihawu starter.
 * Registers [IhawuProperties] and contributes Ihawu's beans, backing off when `ihawu.enabled=false`.
 * Each bean is guarded so applications can override it.
 *
 */
@AutoConfiguration
@Import(IhawuSecurityConfiguration::class)
@EnableConfigurationProperties(IhawuProperties::class)
@ConditionalOnProperty(prefix = "ihawu", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class IhawuAutoConfiguration
