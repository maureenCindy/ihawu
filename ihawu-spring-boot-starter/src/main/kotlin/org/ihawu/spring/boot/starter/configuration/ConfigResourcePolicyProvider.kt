package org.ihawu.spring.boot.starter.configuration

import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.ResourcePolicy
import org.ihawu.spring.boot.starter.configuration.IhawuProperties.PolicyProperties
import org.slf4j.LoggerFactory

/**
 * The default [ResourcePolicyProvider]: maps the static `ihawu.policies` configuration to the core
 * [ResourcePolicy] rules that drive masking.
 *
 * Mapping and validation are eager — they run at construction — so malformed configuration fails the
 * application context at startup, never at the first request:
 * - **duplicate `resource` keys** are rejected; the role-based resolver matches the first entry by
 *   resource name, so a duplicate would silently shadow the later one,
 * - **blank `field` names** are rejected.
 *
 * Kept as a starter-local mapping so `ihawu-core` types stay off the configuration contract (ADR
 * 0004). Applications needing dynamic rules supply their own [ResourcePolicyProvider] bean, and this
 * default backs off.
 *
 * @param policies The bound `ihawu.policies` configuration.
 */
public class ConfigResourcePolicyProvider(
    policies: List<PolicyProperties>,
) : ResourcePolicyProvider {
    private val resourcePolicies: List<ResourcePolicy> = policies.validated().toResourcePolicies()

    override fun getResourcePolicies(): List<ResourcePolicy> = resourcePolicies

    private fun List<PolicyProperties>.validated(): List<PolicyProperties> {
        val duplicates = groupingBy { it.resource }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) {
            "ihawu.policies has duplicate resource keys: ${duplicates.joinToString()}"
        }
        forEach { policy ->
            policy.roles.forEach { (role, fields) ->
                fields.forEach { field ->
                    require(field.field.isNotBlank()) {
                        "ihawu.policies has a blank field name for resource '${policy.resource}', role '$role'"
                    }
                }
            }
        }
        return this
    }

    private fun List<PolicyProperties>.toResourcePolicies(): List<ResourcePolicy> =
        map { policy ->
            ResourcePolicy(
                resourceName = policy.resource,
                roleFieldPolicies =
                    policy.roles.mapValues { (_, fields) ->
                        fields.map { FieldPolicy(it.field, it.strategy, it.placeholder) }
                    },
            )
        }.also(::logSummary)

    private fun logSummary(policies: List<ResourcePolicy>) {
        val summary = policies.joinToString { "${it.resourceName}(roles=${it.roleFieldPolicies?.size ?: 0})" }
        logger.info("Ihawu loaded {} resource policy rule(s) from configuration: {}", policies.size, summary)
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(ConfigResourcePolicyProvider::class.java)
    }
}
