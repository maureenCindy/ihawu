package org.ihawu.sample

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** A policy source that is always "down" — stands in for a policy-store outage. */
private class FailingResolver : ResourcePolicyResolver {
    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> = throw IllegalStateException("policy store unreachable")
}

/**
 * Replaces the starter's default policy source with [FailingResolver]. The bean is named
 * `resourcePolicyResolver` so the starter's request-scoped caching resolver (which selects its delegate
 * by that name) wraps it — exactly where a real resolver would sit.
 */
@TestConfiguration
class OutageResolverConfig {
    @Bean
    fun resourcePolicyResolver(): ResourcePolicyResolver = FailingResolver()
}

/**
 * 0.4.0 observability — **metrics.** A resolver outage masks fail-closed to `{}` (still `200`), and the
 * drop is counted as `ihawu.masking.failures{resource,reason}` (the starter's Micrometer sink activates
 * because `spring-boot-starter-actuator` puts a `MeterRegistry` on the classpath). Alert on
 * `reason=RESOLVER_ERROR`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OutageResolverConfig::class)
class MaskingFailureMetricsTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val meterRegistry: MeterRegistry,
) {
    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `a resolver outage masks to empty at 200 and increments the failure counter`() {
        mockMvc
            .perform(get("/employees/42"))
            .andExpect(status().isOk)
            .andExpect(content().json("{}"))

        assertThat(
            meterRegistry
                .counter("ihawu.masking.failures", "resource", "employee", "reason", "RESOLVER_ERROR")
                .count(),
        ).isGreaterThan(0.0)
    }
}

/**
 * 0.4.0 observability — **failing the request.** With `ihawu.on-policy-failure=fail-request`, the same
 * resolver outage surfaces as a `5xx` instead of a silent `{}`, so it can't hide behind a `200`
 * (ADR 0011).
 */
@SpringBootTest(properties = ["ihawu.on-policy-failure=fail-request"])
@AutoConfigureMockMvc
@Import(OutageResolverConfig::class)
class FailRequestModeTest(
    @Autowired val mockMvc: MockMvc,
) {
    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `fail-request turns a resolver outage into a 500`() {
        mockMvc
            .perform(get("/employees/42"))
            .andExpect(status().is5xxServerError)
    }
}
