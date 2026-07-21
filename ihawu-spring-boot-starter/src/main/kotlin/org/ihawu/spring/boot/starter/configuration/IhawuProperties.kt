package org.ihawu.spring.boot.starter.configuration

import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.masking.ResolverErrorMode
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Typed configuration for the Ihawu starter, bound from `ihawu.*`.
 *
 * @property enabled Master switch for the Ihawu integration - when false the auto-config backs off.
 */
@ConfigurationProperties(prefix = "ihawu")
public class IhawuProperties {
    /**
     * Master switch for the Ihawu integration - when false the auto-config backs off.
     */
    public var enabled: Boolean = true

    /**
     * The static masking rules, bound from `ihawu.policies[]` and mapped to core [org.ihawu.core.policy.ResourcePolicy]
     * rules by the default provider. Defaults to empty — no rules, so nothing is masked — until the
     * application supplies them here or replaces the provider with its own bean (see ADR 0004).
     */
    public var policies: List<PolicyProperties> = emptyList()

    /**
     * Whether to check, at startup, that every configured masking policy can satisfy its resource's
     * declared type contract — failing the application context (see ADR 0005) on a policy that would
     * emit schema-invalid output (e.g. `REDACT` on a non-nullable `Int`, `HIDE` on a non-nullable
     * field). Defaults to true; set false to defer such a policy to the runtime fail-closed backstop.
     */
    public var validateResourceContract: Boolean = true

    /**
     * Packages to scan for [org.ihawu.core.annotation.IhawuResource] types during startup contract
     * validation. Defaults to empty — the application's auto-configuration base packages are used. Set
     * this when resources live outside those packages.
     */
    public var resourceBasePackages: List<String> = emptyList()

    /**
     * How a **policy-resolver failure** (a policy-store outage or misconfiguration) is handled, bound from
     * `ihawu.on-policy-failure` (`mask-all` / `fail-request`). Defaults to
     * [ResolverErrorMode.MASK_ALL] — mask the whole resource fail-closed and return 200.
     * [ResolverErrorMode.FAIL_REQUEST] instead lets the error surface (a `5xx`), so an outage is not
     * silent — see ADR 0011 for the committed-response caveat. Only the resolver-error path is affected;
     * a missing principal always masks fail-closed.
     */
    public var onPolicyFailure: ResolverErrorMode = ResolverErrorMode.MASK_ALL

    /**
     * The config representation of a single field's masking rule, bound from
     * `ihawu.policies[].roles.<role>[]` and mapped to a core [org.ihawu.core.policy.FieldPolicy] by the default provider.
     *
     * Kept as a starter-local binding shape so `ihawu-core`'s `FieldPolicy` never becomes part of the
     * configuration contract (see ADR 0004).
     *
     * @property field The property name to mask. Required; an empty value is rejected at startup.
     * @property strategy How the field is masked, bound by enum name (`HIDE`/`REDACT`). Defaults to
     * the stricter [org.ihawu.core.masking.MaskingStrategy.HIDE] so an under-specified rule errs towards hiding.
     * @property placeholder The replacement written when redacting; ignored by `HIDE`. When null, the
     * strategy's own default value is used.
     */
    public data class FieldPolicyProperties(
        val field: String,
        val strategy: MaskingStrategy = MaskingStrategy.HIDE,
        val placeholder: String? = null,
    )

    /**
     * The config representation of one resource's masking rules, bound from `ihawu.policies[]` and
     * mapped to a core `ResourcePolicy` by the default provider.
     *
     * Kept as a starter-local binding shape so `ihawu-core`'s `ResourcePolicy` never becomes part of
     * the configuration contract (see ADR 0004).
     *
     * @property resource The resource key these rules apply to, matched against the resolved resource
     * name. Required; duplicate keys across entries are rejected at startup.
     * @property roles The per-role [FieldPolicyProperties] lists, keyed by role name. Defaults to
     * empty — a resource with no role rules resolves to no policies, so it serializes unmasked
     * (fail open on missing policy; see ADR 0003). Non-null here, unlike core's nullable map, so the
     * config shape is unambiguous for operators.
     */
    public data class PolicyProperties(
        val resource: String,
        val roles: Map<String, List<FieldPolicyProperties>> = emptyMap(),
    )
}
