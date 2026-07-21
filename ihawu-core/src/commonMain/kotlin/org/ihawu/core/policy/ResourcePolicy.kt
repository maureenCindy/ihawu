package org.ihawu.core.policy

/**
 * The static masking rules for a single resource, consumed by [RoleBasedResourcePolicyResolver].
 *
 * @property resourceName The resource key these rules apply to, matched against the resolved resource name.
 * @property roleFieldPolicies The per-role [FieldPolicy] lists, keyed by role name. `null` (the default)
 * means no role-specific rules are configured for the resource, so resolution yields an empty list.
 */
public data class ResourcePolicy(
    val resourceName: String,
    val roleFieldPolicies: Map<String, List<FieldPolicy>>? = null,
)
