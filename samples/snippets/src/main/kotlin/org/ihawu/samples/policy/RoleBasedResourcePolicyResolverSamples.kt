package org.ihawu.samples.policy

import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicy
import org.ihawu.core.policy.RoleBasedResourcePolicyResolver
import org.ihawu.jackson.JacksonPolicyConfig

fun resolvePoliciesForRole() {
    // Static rules: for the "employee" resource, the MANAGER role redacts salary.
    val rules =
        listOf(
            ResourcePolicy(
                resourceName = "employee",
                roleFieldPolicies =
                    mapOf(
                        "MANAGER" to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, placeholder = "***")),
                    ),
            ),
        )
    val resolver = RoleBasedResourcePolicyResolver(rules)

    val principal = IhawuPrincipal("u1", roles = setOf("MANAGER"), attributes = emptyMap())
    val policies = resolver.resolve(principal, "employee")

    check(policies == listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "***")))
}

fun mostRestrictiveStrategyWinsAcrossRoles() {
    // The principal holds two roles that mask the same field; HIDE outranks REDACT.
    val rules =
        listOf(
            ResourcePolicy(
                resourceName = "employee",
                roleFieldPolicies =
                    mapOf(
                        "MANAGER" to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, placeholder = "***")),
                        "AUDITOR" to listOf(FieldPolicy("salary", MaskingStrategy.HIDE)),
                    ),
            ),
        )
    val resolver = RoleBasedResourcePolicyResolver(rules)

    val principal = IhawuPrincipal("u1", roles = setOf("MANAGER", "AUDITOR"), attributes = emptyMap())
    val policies = resolver.resolve(principal, "employee")

    check(policies == listOf(FieldPolicy("salary", MaskingStrategy.HIDE)))
}

fun loadResolverFromJson() {
    // The same rules expressed as JSON configuration: resource -> role -> field policies.
    val config =
        """
        {
          "employee": {
            "MANAGER": [ { "field": "salary", "strategy": "REDACT", "placeholder": "***" } ]
          }
        }
        """.trimIndent()
    val resolver = JacksonPolicyConfig.fromJson(config)

    val principal = IhawuPrincipal("u1", roles = setOf("MANAGER"), attributes = emptyMap())
    val policies = resolver.resolve(principal, "employee")

    check(policies == listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "***")))
}
