package com.ihawu.spring.boot.starter.configuration

import com.ihawu.core.policy.ResourcePolicy
import com.ihawu.core.policy.ResourcePolicyResolver
import com.ihawu.core.policy.RoleBasedResourcePolicyResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Provides the default policy source that drives masking.
 *
 * Not gated on Spring Security — the resolver must exist for masking to work in any app, with or
 * without an authenticated principal. Supplies a [RoleBasedResourcePolicyResolver] over a
 * (by default empty) list of [ResourcePolicy] rules; both beans are `@ConditionalOnMissingBean` so
 * applications replace either the rules or the whole resolver.
 */
@Configuration(proxyBeanMethods = false)
class ResourcePolicyConfig {
    /**
     * The static masking rules backing the default resolver.
     *
     * Defaults to **empty — no policies, so nothing is masked** until rules are supplied. Override by
     * defining your own bean named `resourcePolicies`. Binding these from `ihawu.*` configuration is
     * tracked separately (#21); for now they are provided programmatically.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["resourcePolicies"])
    fun resourcePolicies(): List<ResourcePolicy> = emptyList()

    /**
     * The default [ResourcePolicyResolver]: a [RoleBasedResourcePolicyResolver] over [resourcePolicies].
     * Replace it by defining your own [ResourcePolicyResolver] bean (e.g. backed by OPA or a database).
     */
    @Bean
    @ConditionalOnMissingBean
    fun resourcePolicyResolver(resourcePolicies: List<ResourcePolicy>): ResourcePolicyResolver =
        RoleBasedResourcePolicyResolver(resourcePolicies)
}
