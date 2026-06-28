package com.ihawu.spring.boot.starter

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * Autoconfiguration entry point for the Ihawu starter.
 * Registers [IhawuProperties] and contributes Ihawu's beans, backing off when `ihawu.enabled=false`.
 * Each bean is guarded so applications can override it.
 *
 */
@AutoConfiguration
@EnableConfigurationProperties(IhawuProperties::class)
@ConditionalOnProperty(prefix = "ihawu", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class IhawuAutoConfiguration
