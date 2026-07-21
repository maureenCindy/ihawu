package org.ihawu.core.policy

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
 * - **Most-restrictive-wins** — when two roles mask the same field, the stricter
 *   [org.ihawu.core.masking.MaskingStrategy] is kept (e.g. `HIDE` outranks `REDACT`).
 * - **Deterministic** — ties between equal strategies are broken by role-name order, so the result
 *   never depends on [Set] iteration order.
 * - **Fail-open on absence** — an unknown resource, a role with no configured policy, or a principal
 *   with no roles yields an empty list; the caller decides default visibility.
 *
 * Rules are supplied directly as a [List] of [ResourcePolicy]. To load them from JSON configuration
 * instead, use `JacksonPolicyConfig.fromJson` in the `ihawu-jackson` module.
 *
 * @param resourcePolicies The static `resource -> role -> field policies` rules to resolve against.
 * @sample org.ihawu.samples.policy.resolvePoliciesForRole
 * @sample org.ihawu.samples.policy.mostRestrictiveStrategyWinsAcrossRoles
 * @see ResourcePolicy
 */
public class RoleBasedResourcePolicyResolver(
    private val resourcePolicies: List<ResourcePolicy>,
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
}
