package com.ihawu.spring.boot.starter.configuration

import com.ihawu.core.masking.MaskingStrategy
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Typed configuration for the Ihawu starter, bound from `ihawu.*`.
 *
 * @property enabled Master switch for the Ihawu integration - when false the auto-config backs off.
 */
@ConfigurationProperties(prefix = "ihawu")
class IhawuProperties {
    /**
     * Master switch for the Ihawu integration - when false the auto-config backs off.
     */
    var enabled: Boolean = true

    /**
     * The static masking rules, bound from `ihawu.policies[]` and mapped to core [com.ihawu.core.policy.ResourcePolicy]
     * rules by the default provider. Defaults to empty — no rules, so nothing is masked — until the
     * application supplies them here or replaces the provider with its own bean (see ADR 0004).
     */
    var policies: List<PolicyProperties> = emptyList()

    /**
     * The config representation of a single field's masking rule, bound from
     * `ihawu.policies[].roles.<role>[]` and mapped to a core [com.ihawu.core.policy.FieldPolicy] by the default provider.
     *
     * Kept as a starter-local binding shape so `ihawu-core`'s `FieldPolicy` never becomes part of the
     * configuration contract (see ADR 0004).
     *
     * @property field The property name to mask. Required; an empty value is rejected at startup.
     * @property strategy How the field is masked, bound by enum name (`HIDE`/`REDACT`). Defaults to
     * the stricter [com.ihawu.core.masking.MaskingStrategy.HIDE] so an under-specified rule errs towards hiding.
     * @property placeholder The replacement written when redacting; ignored by `HIDE`. When null, the
     * strategy's own default value is used.
     */
    data class FieldPolicyProperties(
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
    data class PolicyProperties(
        val resource: String,
        val roles: Map<String, List<FieldPolicyProperties>> = emptyMap(),
    )
}
