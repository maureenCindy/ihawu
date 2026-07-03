package org.ihawu.spring.boot.starter.configuration

import org.ihawu.core.policy.CachingResourcePolicyResolver
import org.ihawu.core.policy.ResourcePolicyResolver
import org.ihawu.core.policy.RoleBasedResourcePolicyResolver
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.web.context.WebApplicationContext

/**
 * Provides the default policy source that drives masking.
 *
 * Not gated on Spring Security — the resolver must exist for masking to work in any app, with or
 * without an authenticated principal. Supplies a [RoleBasedResourcePolicyResolver] that is
 * `@ConditionalOnMissingBean` so applications replace either the rules or the whole resolver.
 */
@Configuration(proxyBeanMethods = false)
internal class ResourcePolicyConfig {
    @Bean
    @ConditionalOnMissingBean
    fun resourcePolicyProvider(properties: IhawuProperties): ResourcePolicyProvider = ConfigResourcePolicyProvider(properties.policies)

    /**
     * The default [ResourcePolicyResolver]: a [RoleBasedResourcePolicyResolver] over the rules from
     * [ResourcePolicyProvider]. Replace it by defining your own [ResourcePolicyResolver] bean (e.g.
     * backed by OPA or a database).
     */
    @Bean
    @ConditionalOnMissingBean
    fun resourcePolicyResolver(resourcePolicyProvider: ResourcePolicyProvider): ResourcePolicyResolver =
        RoleBasedResourcePolicyResolver(resourcePolicyProvider.getResourcePolicies())

    /**
     * The request-scoped caching layer over [resourcePolicyResolver], registered as the `@Primary`
     * [ResourcePolicyResolver] so consumers (e.g. `IhawuModule`) resolve through it.
     *
     * A fresh [CachingResourcePolicyResolver] per HTTP request gives the cache a request lifetime: the
     * delegate is hit at most once per `(principal, resource)` within a request, and the cache is
     * discarded between requests so policy stays fresh and never bleeds across requests. The bean is a
     * scoped proxy ([ScopedProxyMode.INTERFACES]) so the singleton consumer can hold a stable
     * reference while each call routes to the current request's instance; interface proxying is used
     * because the core [CachingResourcePolicyResolver] is `final`.
     *
     * The delegate is selected by [Qualifier] rather than by type: this bean is `@Primary`, so a
     * by-type lookup would resolve to itself and self-wrap. Gated to servlet web apps — off a request
     * thread there is no request scope, so non-web apps fall back to the plain [resourcePolicyResolver].
     */
    @Bean
    @Primary
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.INTERFACES)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(name = ["cachingResourcePolicyResolver"])
    fun cachingResourcePolicyResolver(
        @Qualifier("resourcePolicyResolver") delegate: ResourcePolicyResolver,
    ): ResourcePolicyResolver = CachingResourcePolicyResolver(delegate)
}
