package com.ihawu.core.policy

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ihawu.core.exception.IhawuCoreException
import com.ihawu.core.masking.MaskingStrategy
import java.io.InputStream

/**
 * A configuration-backed [ResourcePolicyResolver] that resolves static, role-based masking rules.
 *
 * This is Ihawu's batteries-included resolver: the default, no-external-dependency way to drive
 * masking without standing up an external engine (OPA, Casbin, ...). Rules are supplied up front as
 * a list of [ResourcePolicy] entries mapping `resource -> role -> field policies`; resolution is a
 * pure lookup against the principal's roles. Ihawu stays a Policy Enforcement Point — the
 * configuration *is* the pre-resolved policy, and no conditions are evaluated here.
 *
 * Resolution semantics:
 * - **Union across roles** — the field policies of every role the principal holds are combined.
 * - **Most-restrictive-wins** — when two roles mask the same field, the strategy with the higher
 *   [com.ihawu.core.masking.MaskingStrategy.securityLevel] is kept (e.g. `HIDE` outranks `REDACT`).
 * - **Deterministic** — ties between equal strategies are broken by role-name order, so the result
 *   never depends on [Set] iteration order.
 * - **Fail-open on absence** — an unknown resource, a role with no configured policy, or a principal
 *   with no roles yields an empty list; the caller decides default visibility.
 *
 * Rules may be supplied directly as a [List] of [ResourcePolicy], or loaded from JSON configuration
 * via [fromJson]. The configuration is a JSON object keyed by resource name; each resource maps role
 * names to an array of field policies. `placeholder` is optional. Parsing is eager and fail-fast — an
 * invalid document or rule throws [IhawuCoreException] at load time, never at request time:
 *
 * ```json
 * {
 *   "employee": {
 *     "MANAGER": [ { "field": "salary", "strategy": "REDACT", "placeholder": "***" } ],
 *     "AUDITOR": [ { "field": "ssn", "strategy": "HIDE" } ]
 *   }
 * }
 * ```
 *
 * @property resourcePolicies The static `resource -> role -> field policies` rules to resolve against.
 * @sample com.ihawu.samples.policy.resolvePoliciesForRole
 * @sample com.ihawu.samples.policy.mostRestrictiveStrategyWinsAcrossRoles
 * @sample com.ihawu.samples.policy.loadResolverFromJson
 * @see ResourcePolicy
 */
class RoleBasedResourcePolicyResolver(
    val resourcePolicies: List<ResourcePolicy>,
) : ResourcePolicyResolver {
    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> {
        val policy = resourcePolicies.firstOrNull { it.resourceName == resource }
        if (policy == null) {
            return emptyList()
        }
        val principalRolesFieldPolicyList = mutableListOf<FieldPolicy>()
        principal.roles.sorted().forEach { role ->
            policy.roleFieldPolicies?.get(role)?.forEach { principalRolesFieldPolicyList.add(it) }
        }

        val fieldPolicyMap = mutableMapOf<String, FieldPolicy>()
        principalRolesFieldPolicyList.forEach { fieldPolicy ->
            val existing = fieldPolicyMap[fieldPolicy.field]
            if (existing == null || existing.strategy.securityLevel < fieldPolicy.strategy.securityLevel) {
                fieldPolicyMap[fieldPolicy.field] = fieldPolicy
            }
        }
        return fieldPolicyMap.values.toList()
    }

    companion object {
        private val mapper = ObjectMapper()

        /**
         * Builds a resolver from a JSON configuration [String]. See the class documentation for the
         * schema. Parsing is eager and fail-fast.
         *
         * @throws IhawuCoreException if the JSON is invalid or any rule is malformed.
         */
        fun fromJson(json: String): RoleBasedResourcePolicyResolver = fromTree(readTree { mapper.readTree(json) })

        /**
         * Builds a resolver from a JSON configuration [InputStream] (e.g. a classpath resource). See
         * the class documentation for the schema. Parsing is eager and fail-fast.
         *
         * @throws IhawuCoreException if the JSON is invalid or any rule is malformed.
         */
        fun fromJson(input: InputStream): RoleBasedResourcePolicyResolver = fromTree(readTree { mapper.readTree(input) })

        private inline fun readTree(read: () -> JsonNode?): JsonNode =
            try {
                // Defensive: current Jackson returns a MissingNode (caught by the isObject check
                // in fromTree), not null, for empty input — so this guards a contract that could
                // change rather than a path reachable today.
                read() ?: throw IhawuCoreException("Policy configuration is empty")
            } catch (e: IhawuCoreException) {
                throw e
            } catch (e: Exception) {
                throw IhawuCoreException("Policy configuration is not valid JSON", e)
            }

        private fun fromTree(root: JsonNode): RoleBasedResourcePolicyResolver {
            if (!root.isObject) {
                throw IhawuCoreException("Policy configuration must be a JSON object of resource -> role -> policies")
            }
            val policies =
                root
                    .properties()
                    .map { (resource, roles) ->
                        if (!roles.isObject) {
                            throw IhawuCoreException("Resource '$resource' must map role names to field policies")
                        }
                        val roleFieldPolicies =
                            roles.properties().associate { (role, fields) ->
                                if (!fields.isArray) {
                                    throw IhawuCoreException("Role '$role' in resource '$resource' must be an array of field policies")
                                }
                                role to fields.map { fieldPolicy(it, resource, role) }
                            }
                        ResourcePolicy(resource, roleFieldPolicies)
                    }.toList()
            return RoleBasedResourcePolicyResolver(policies)
        }

        private fun fieldPolicy(
            node: JsonNode,
            resource: String,
            role: String,
        ): FieldPolicy {
            val field =
                node.get("field")?.takeIf { it.isTextual }?.asText()
                    ?: throw IhawuCoreException("A field policy for role '$role' in resource '$resource' is missing a textual 'field'")
            val strategyName =
                node.get("strategy")?.takeIf { it.isTextual }?.asText()
                    ?: throw IhawuCoreException("Field '$field' (role '$role', resource '$resource') is missing a textual 'strategy'")
            val strategy =
                runCatching { MaskingStrategy.valueOf(strategyName) }.getOrElse {
                    throw IhawuCoreException(
                        "Field '$field' (role '$role', resource '$resource') has unknown strategy '$strategyName'; " +
                            "expected one of ${MaskingStrategy.entries.joinToString { it.name }}",
                    )
                }
            val placeholder = node.get("placeholder")?.takeIf { it.isTextual }?.asText()
            return FieldPolicy(field, strategy, placeholder)
        }
    }
}
