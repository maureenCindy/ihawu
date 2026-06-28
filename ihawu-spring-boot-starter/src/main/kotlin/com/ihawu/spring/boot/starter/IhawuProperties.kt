package com.ihawu.spring.boot.starter

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Typed configuration for the Ihawu starter, bound from `ihawu.*`.
 *
 * @property enabled Master switch for the Ihawu integration - when false the auto-config backs off.
 */
@ConfigurationProperties(prefix = "ihawu")
class IhawuProperties {
    /**
     * Master switch for the Ihawu integration - when false the auto-config backs off.
     */
    var enabled: Boolean = true
}
