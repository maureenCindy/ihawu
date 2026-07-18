package org.ihawu.core.policy

/**
 * Strategy for resolving the masking policies that apply to a resource requested for a given principal.
 *
 * This is Ihawu's integration point with external policy engines (e.g. config, database, OPA, Casbin).
 * Ihawu stays a Policy Enforcement Point, it enforces the returned [FieldPolicy] list and never evaluates policy
 * conditions itself.
 *
 * Before writing your own, consider the batteries-included implementations: [RoleBasedResourcePolicyResolver]
 * for static config-driven rules, and [CachingResourcePolicyResolver] to memoize any resolver.
 *
 * @see RoleBasedResourcePolicyResolver
 * @see CachingResourcePolicyResolver
 */
interface ResourcePolicyResolver {
    /**
     * Resolves the [resource] field policies that apply when [principal] requests [resource].
     *
     * @param principal the authenticated identity requesting the resource
     * @param resource the name of the resource whose field policies should be resolved
     * @return [FieldPolicy] list defining which fields are masked
     */
    fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy>
}
