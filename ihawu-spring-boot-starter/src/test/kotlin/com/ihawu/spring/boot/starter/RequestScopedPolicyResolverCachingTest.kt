package com.ihawu.spring.boot.starter

import com.ihawu.core.policy.IhawuPrincipal
import com.ihawu.core.policy.ResourcePolicyResolver
import com.ihawu.core.policy.RoleBasedResourcePolicyResolver
import com.ihawu.spring.boot.starter.configuration.ResourcePolicyProvider
import com.ihawu.spring.boot.starter.fixture.MaskingSampleWebApp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proves that policy resolution is cached **per HTTP request**, keyed by `(principal, resource)`.
 *
 * The starter's request-scoped [com.ihawu.core.policy.CachingResourcePolicyResolver] wraps the
 * delegate replaced here by a [CountingResolver], so its [CountingResolver.calls] counter is the
 * number of times the underlying resolver was actually consulted.
 *
 * Two properties matter and are tested separately:
 * - **Within a request**, repeated resources of the same type resolve once (caching is engaged).
 * - **Across requests**, the cache is fresh — request two consults the delegate again rather than
 *   serving a stale, cross-request result. This is the property the request scope exists to provide;
 *   a singleton cache would leave the count unchanged on the second request.
 */
@SpringBootTest(classes = [MaskingSampleWebApp::class, RequestScopedPolicyResolverCachingTest.CountingResolverConfig::class])
@AutoConfigureMockMvc
class RequestScopedPolicyResolverCachingTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val countingResolver: CountingResolver,
) {
    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `resolves a resource policy once per request despite many resources of the same type`() {
        countingResolver.calls.set(0)

        // The response holds five employees (same principal, same "employee" resource) with no
        // contacts, so an uncached resolver would be consulted five times; a cached one, once.
        mockMvc.perform(get("/employees")).andExpect(status().isOk)

        assertThat(countingResolver.calls.get()).isEqualTo(1)
    }

    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `resolves resource policies again on a second request so the cache does not bleed across requests`() {
        countingResolver.calls.set(0)

        mockMvc.perform(get("/employee")).andExpect(status().isOk)
        val afterFirstRequest = countingResolver.calls.get()
        assertThat(afterFirstRequest).isGreaterThan(0)

        mockMvc.perform(get("/employee")).andExpect(status().isOk)
        val afterSecondRequest = countingResolver.calls.get()

        // A fresh request-scoped cache each request ⇒ the second request consults the delegate again,
        // repeating exactly the first request's resolutions. A singleton (global) cache — the bug the
        // request scope guards against — would leave the count at its first-request value.
        assertThat(afterSecondRequest - afterFirstRequest).isEqualTo(afterFirstRequest)
    }

    /**
     * Replaces the starter's default `resourcePolicyResolver` (the delegate) with a counting decorator
     * over the same role-based rules. Named `resourcePolicyResolver` so the `@Primary` caching bean's
     * `@Qualifier("resourcePolicyResolver")` delegate resolves to it.
     */
    @TestConfiguration
    class CountingResolverConfig {
        @Bean
        fun resourcePolicyResolver(provider: ResourcePolicyProvider): CountingResolver =
            CountingResolver(RoleBasedResourcePolicyResolver(provider.getResourcePolicies()))
    }

    class CountingResolver(
        private val delegate: ResourcePolicyResolver,
    ) : ResourcePolicyResolver {
        val calls = AtomicInteger(0)

        override fun resolve(
            principal: IhawuPrincipal,
            resource: String,
        ) = delegate.resolve(principal, resource).also { calls.incrementAndGet() }
    }
}
