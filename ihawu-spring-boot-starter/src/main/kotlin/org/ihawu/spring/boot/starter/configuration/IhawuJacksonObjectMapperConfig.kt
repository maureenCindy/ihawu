package org.ihawu.spring.boot.starter.configuration

import org.ihawu.core.policy.ResourcePolicyResolver
import org.ihawu.jackson.IhawuModule
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
    /** The core masking [IhawuModule], resolving field policies through the configured [resolver]. */
    @Bean
    @ConditionalOnMissingBean
    fun ihawuModule(resolver: ResourcePolicyResolver): IhawuModule = IhawuModule(resolver)
}
