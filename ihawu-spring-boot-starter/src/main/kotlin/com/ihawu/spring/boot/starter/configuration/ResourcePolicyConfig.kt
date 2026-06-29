package com.ihawu.spring.boot.starter.configuration

import com.ihawu.core.policy.ResourcePolicyResolver
import com.ihawu.core.policy.RoleBasedResourcePolicyResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Provides the default policy source that drives masking.
 *
 * Not gated on Spring Security — the resolver must exist for masking to work in any app, with or
 * without an authenticated principal. Supplies a [RoleBasedResourcePolicyResolver] that is
 * `@ConditionalOnMissingBean` so applications replace either the rules or the whole resolver.
 */
@Configuration(proxyBeanMethods = false)
class ResourcePolicyConfig {
    @Bean
    @ConditionalOnMissingBean
    fun resourcePolicyProvider(): ResourcePolicyProvider = ResourcePolicyProvider { emptyList() }

    /**
     * The default [ResourcePolicyResolver]: a [RoleBasedResourcePolicyResolver] over the rules from
     * [ResourcePolicyProvider]. Replace it by defining your own [ResourcePolicyResolver] bean (e.g.
     * backed by OPA or a database).
     */
    @Bean
    @ConditionalOnMissingBean
    fun resourcePolicyResolver(resourcePolicyProvider: ResourcePolicyProvider): ResourcePolicyResolver =
        RoleBasedResourcePolicyResolver(resourcePolicyProvider.getResourcePolicies())
}
