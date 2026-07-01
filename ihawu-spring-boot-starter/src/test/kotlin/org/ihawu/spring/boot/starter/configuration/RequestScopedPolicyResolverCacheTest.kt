package org.ihawu.spring.boot.starter.configuration

import org.assertj.core.api.Assertions
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicy
import org.ihawu.core.policy.ResourcePolicyResolver
import org.ihawu.core.policy.RoleBasedResourcePolicyResolver
import org.ihawu.spring.boot.starter.fixture.EmployeeTestController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proves that policy resolution is cached **per HTTP request**, keyed by `(principal, resource)`.
 *
 * The starter's request-scoped [org.ihawu.core.policy.CachingResourcePolicyResolver] wraps the
 * delegate replaced here by a [CountingResolver], so its [CountingResolver.calls] counter is the
 * number of times the underlying resolver was actually consulted.
 *
 * Two properties matter and are tested separately:
 * - **Within a request**, repeated resources of the same type resolve once (caching is engaged).
 * - **Across requests**, the cache is fresh — request two consults the delegate again rather than
 *   serving a stale, cross-request result. This is the property the request scope exists to provide;
 *   a singleton cache would leave the count unchanged on the second request.
 */
@SpringBootTest(
    classes = [
        RequestScopedPolicyResolverCacheTest.SampleMaskingWebApp::class,
    ],
)
@AutoConfigureMockMvc
class RequestScopedPolicyResolverCacheTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val countingResolver: CountingResolver,
) {
    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `resolves a resource policy once per request despite many resources of the same type`() {
        countingResolver.calls.set(0)

        // The response holds five employees (same principal, same "employee" resource) with no
        // contacts, so an uncached resolver would be consulted five times; a cached one, once.
        mockMvc.perform(MockMvcRequestBuilders.get("/employees")).andExpect(MockMvcResultMatchers.status().isOk)

        Assertions.assertThat(countingResolver.calls.get()).isEqualTo(1)
    }

    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `resolves resource policies again on a second request so the cache does not bleed across requests`() {
        countingResolver.calls.set(0)

        mockMvc.perform(MockMvcRequestBuilders.get("/employee")).andExpect(MockMvcResultMatchers.status().isOk)
        val afterFirstRequest = countingResolver.calls.get()
        Assertions.assertThat(afterFirstRequest).isGreaterThan(0)

        mockMvc.perform(MockMvcRequestBuilders.get("/employee")).andExpect(MockMvcResultMatchers.status().isOk)
        val afterSecondRequest = countingResolver.calls.get()

        // A fresh request-scoped cache each request ⇒ the second request consults the delegate again,
        // repeating exactly the first request's resolutions. A singleton (global) cache — the bug the
        // request scope guards against — would leave the count at its first-request value.
        Assertions.assertThat(afterSecondRequest - afterFirstRequest).isEqualTo(afterFirstRequest)
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

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(EmployeeTestController::class)
    class SampleMaskingWebApp {
        /**
         * Replaces the starter's default `resourcePolicyResolver` (the delegate) with a counting decorator
         * over the same role-based rules. Named `resourcePolicyResolver` so the `@Primary` caching bean's
         * `@Qualifier("resourcePolicyResolver")` delegate resolves to it.
         */
        @Bean
        fun resourcePolicyResolver(provider: ResourcePolicyProvider): CountingResolver =
            CountingResolver(RoleBasedResourcePolicyResolver(provider.getResourcePolicies()))

        @Bean
        fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
            http
                .authorizeHttpRequests {
                    it
                        .requestMatchers("/public/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                }.build()

        @Bean
        fun resourcePolicyProvider(): ResourcePolicyProvider =
            ResourcePolicyProvider {
                listOf(
                    ResourcePolicy(
                        resourceName = "employee",
                        roleFieldPolicies =
                            mapOf(
                                "ADMIN" to
                                    listOf(
                                        FieldPolicy("salary", MaskingStrategy.HIDE),
                                        FieldPolicy("ssn", MaskingStrategy.REDACT, "***ssn"),
                                    ),
                            ),
                    ),
                    ResourcePolicy(
                        resourceName = "contact",
                        roleFieldPolicies =
                            mapOf(
                                "ADMIN" to
                                    listOf(
                                        FieldPolicy("homeAddress", MaskingStrategy.HIDE),
                                        FieldPolicy("phone", MaskingStrategy.REDACT, "+263***"),
                                    ),
                            ),
                    ),
                )
            }
    }
}
