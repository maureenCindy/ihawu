package org.ihawu.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import org.ihawu.core.masking.FailReason
import org.ihawu.core.masking.MaskingCapability
import org.ihawu.core.policy.FieldPolicy

/**
 * A masking policy whose output cannot satisfy the field's declared type contract.
 *
 * @property resource The `@IhawuResource` name the offending policy targets.
 * @property field The field the policy masks.
 * @property reason A human-readable explanation, phrased as remediation.
 */
data class MaskingContractViolation(
    val resource: String,
    val field: String,
    val reason: String,
)

/**
 * Checks masking policies against the declared type contract of their resource, so a policy that would
 * emit schema-invalid output is caught up front rather than at serialization time.
 *
 * This is the reporting half of the guarantee: it returns the violations and never throws, leaving the
 * enforcement decision (the starter fails the application context; ADR 0005) to the caller.
 * Capabilities are computed with the supplied [ObjectMapper]'s introspection, so the verdict matches
 * exactly what [IhawuBeanSerializerModifier] will do at serialization time — including `@JsonProperty`
 * renames and any configured naming strategy.
 *
 * Two failure modes are reported:
 * - a policy that cannot produce contract-valid output (`REDACT` on a non-nullable non-`String`, or
 *   `HIDE` on a non-nullable field);
 * - a policy whose field is not a serialized property of the resource, which masks nothing and so
 *   silently leaves a sensitive field exposed (the under-masking failure mode of ADR 0004).
 */
object MaskingContractValidator {
    /**
     * @param mapper The application's mapper, used to introspect [resourceType] the same way masking
     *   will at runtime.
     * @param resource The resource name, used only to label violations.
     * @param resourceType The `@IhawuResource`-annotated class the [policies] apply to.
     * @param policies The field policies to check (typically every role's rules for [resource]).
     * @return the contract violations; empty means every matched policy is type-safe.
     */
    fun validate(
        mapper: ObjectMapper,
        resource: String,
        resourceType: Class<*>,
        policies: List<FieldPolicy>,
    ): List<MaskingContractViolation> {
        val capabilities = MaskingCapabilities.of(mapper.serializationConfig, resourceType)
        return policies.mapNotNull { policy ->
            reasonFor(policy, capabilities[policy.field])
                ?.let { MaskingContractViolation(resource, policy.field, it) }
        }
    }

    /** The violation reason for [policy] given its field's [capability] (null if the field is unknown), or null if valid. */
    private fun reasonFor(
        policy: FieldPolicy,
        capability: MaskingCapability?,
    ): String? =
        when {
            capability == null ->
                "field is not a serialized property of the resource; the policy masks nothing — check the field name"
            // Reuse the engine's own predicate so the startup check matches runtime behaviour exactly.
            // unenforceableReason only ever yields the two type-contract reasons for a matched field.
            else ->
                capability.unenforceableReason(policy.strategy)?.let { reason ->
                    if (reason == FailReason.HIDE_NON_NULLABLE) {
                        "HIDE cannot omit a non-nullable field; use REDACT, or declare the field nullable/optional"
                    } else {
                        "REDACT cannot mask a non-nullable non-String field; declare it nullable or expose it as a String"
                    }
                }
        }
}
