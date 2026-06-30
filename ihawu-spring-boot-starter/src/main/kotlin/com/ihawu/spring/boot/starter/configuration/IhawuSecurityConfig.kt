package com.ihawu.spring.boot.starter.configuration

import com.ihawu.spring.boot.starter.security.IhawuPrincipalCaptureFilter
import com.ihawu.spring.boot.starter.security.IhawuPrincipalResolver
import com.ihawu.spring.boot.starter.security.PrincipalResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.Authentication

/**
 * Wires Spring Security into Ihawu's identity pipeline.
 *
 * Gated on [Authentication] so the starter still loads in apps without Spring Security (they supply
 * their own principal source). When present, it contributes the default [PrincipalResolver] and the
 * [IhawuPrincipalCaptureFilter] that captures the per-request principal. Both beans are
 * `@ConditionalOnMissingBean` so applications can override them.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Authentication::class)
class IhawuSecurityConfig {
    /** Default mapping from Spring Security's `Authentication` to [com.ihawu.core.policy.IhawuPrincipal]. */
    @Bean
    @ConditionalOnMissingBean
    fun principalResolver(): PrincipalResolver = IhawuPrincipalResolver()

    /** Per-request filter that resolves the principal and exposes it for the serialization bridge. */
    @Bean
    @ConditionalOnMissingBean
    fun ihawuPrincipalCaptureFilter(resolver: PrincipalResolver): IhawuPrincipalCaptureFilter = IhawuPrincipalCaptureFilter(resolver)
}
