package com.ihawu.sample

import com.ihawu.core.masking.MaskingStrategy
import com.ihawu.core.policy.FieldPolicy
import com.ihawu.core.policy.ResourcePolicy
import com.ihawu.spring.boot.starter.configuration.ResourcePolicyProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Static, role-based masking rules for the `employee` resource, supplied programmatically via a
 * [ResourcePolicyProvider] bean (the starter's empty default backs off when this bean is present).
 *
 * The resulting per-role visibility of an `employee`:
 * - **`HR_ADMIN`** — no rules, so the full record is returned (fail-open on missing policy, ADR 0003).
 * - **`MANAGER`** — salary and performance notes stay visible, but the SSN is redacted.
 * - **`EMPLOYEE`** — salary, SSN, and performance notes are hidden entirely.
 *
 * Active only under the **`provider`** profile. The **`config`** profile instead drives the same rules
 * from `ihawu.policies` in `application-config.yml`, exercising the starter's config-backed default.
 */
@Configuration
@Profile("provider")
class PolicyConfig {
    @Bean
    fun resourcePolicyProvider(): ResourcePolicyProvider =
        ResourcePolicyProvider {
            listOf(
                ResourcePolicy(
                    resourceName = "employee",
                    roleFieldPolicies =
                        mapOf(
                            "MANAGER" to
                                listOf(
                                    FieldPolicy("socialSecurityNumber", MaskingStrategy.REDACT, "***-**-****"),
                                ),
                            "EMPLOYEE" to
                                listOf(
                                    FieldPolicy("salary", MaskingStrategy.HIDE),
                                    FieldPolicy("socialSecurityNumber", MaskingStrategy.HIDE),
                                    FieldPolicy("performanceNotes", MaskingStrategy.HIDE),
                                ),
                        ),
                ),
            )
        }
}
