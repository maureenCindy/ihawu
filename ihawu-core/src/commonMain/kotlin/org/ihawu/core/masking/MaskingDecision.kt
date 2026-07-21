package org.ihawu.core.masking

/**
 * The masked outcome for one field: decided by a [MaskingEngine] and executed by a serialization
 * backend. The engine renders the decision fully (down to the concrete value written), so the declared
 * type contract lives in one neutral place rather than being re-derived by each backend.
 */
public sealed interface MaskingDecision {
    /** Write the field's real value, unchanged. */
    public data object Pass : MaskingDecision

    /**
     * Omit the field from the output. [reason] is `null` for a contract-safe omission (a `HIDE` on a
     * nullable/optional field) and non-null when the field was dropped **fail-closed**, so a backend
     * can surface it (see [MaskingFailureSink]).
     */
    public data class Omit(
        val reason: FailReason? = null,
    ) : MaskingDecision

    /** Write [value] — a string placeholder — in place of the real value. */
    public data class WriteString(
        val value: String,
    ) : MaskingDecision

    /** Write JSON null in place of the real value (a nullable non-textual field being redacted). */
    public data object WriteNull : MaskingDecision
}

/** Why a field was dropped fail-closed — for observability (logging today; metrics/alerting later). */
public enum class FailReason {
    /** No principal was attached to the call, so the whole resource is masked. */
    NO_PRINCIPAL,

    /** The policy resolver threw (a misconfiguration or policy-store outage), so the resource is masked. */
    RESOLVER_ERROR,

    /** `HIDE` targeted a non-nullable field; omitting it would break the schema. */
    HIDE_NON_NULLABLE,

    /** `REDACT` targeted a non-nullable non-`String` field, which has no contract-safe masked value. */
    REDACT_UNSAFE,
}
