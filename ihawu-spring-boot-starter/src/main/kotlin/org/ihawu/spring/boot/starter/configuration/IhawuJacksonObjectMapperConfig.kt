package org.ihawu.spring.boot.starter.configuration

import org.ihawu.core.masking.MaskingFailureSink
import org.ihawu.core.policy.ResourcePolicyResolver
import org.ihawu.jackson.IhawuModule
import org.ihawu.jackson.Slf4jMaskingFailureSink
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Installs the core masking module into the application's Jackson `ObjectMapper`.
 *
 * Contributing [IhawuModule] as a bean is enough: Spring Boot auto-registers every Jackson `Module`
 * bean onto its autoconfigured `ObjectMapper`, so `@IhawuResource` graphs are masked without manual
 * wiring and without replacing the app's mapper. The per-request principal is supplied separately by
 * [org.ihawu.spring.boot.starter.interceptor.IhawuJacksonHttpMessageConverter].
 */
@Configuration(proxyBeanMethods = false)
internal class IhawuJacksonObjectMapperConfig {
    /**
     * The core masking [IhawuModule], resolving field policies through the configured [resolver].
     *
     * Uses the application's [MaskingFailureSink] bean when present (e.g. the Micrometer composite from
     * [IhawuObservabilityConfig], or a user-supplied one), falling back to plain SLF4J logging otherwise.
     * The resolver-error failure mode comes from `ihawu.on-policy-failure` (ADR 0011).
     */
    @Bean
    @ConditionalOnMissingBean
    fun ihawuModule(
        resolver: ResourcePolicyResolver,
        failureSink: ObjectProvider<MaskingFailureSink>,
        properties: IhawuProperties,
    ): IhawuModule =
        IhawuModule(
            resolver,
            failureSink.getIfAvailable { Slf4jMaskingFailureSink() },
            properties.onPolicyFailure,
        )
}
