package org.ihawu.core.masking

/**
 * How a single field may be masked so the output still satisfies the field's declared type contract.
 *
 * Derived once per type from two serialization-neutral facts about the declared field — textual-or-not
 * and nullable-or-not — which together decide what each strategy may do: `REDACT` depends on
 * textual-vs-not (a placeholder for text, JSON null for a nullable non-text field, rejected otherwise);
 * `HIDE` depends only on nullability (see [omittable]). A serialization backend computes these two
 * facts from its own type system and calls [of]; nothing here references a serialization library.
 */
public enum class MaskingCapability {
    /** Textual, non-null. REDACT -> placeholder; HIDE -> reject (omitting a required field breaks the schema). */
    TEXTUAL_REQUIRED,

    /** Textual, nullable. REDACT -> placeholder; HIDE -> omit. */
    TEXTUAL_OPTIONAL,

    /** Non-textual, nullable. REDACT -> JSON null; HIDE -> omit. */
    NULLABLE,

    /** Non-textual, non-null. No contract-safe masked form exists; both strategies reject. */
    UNSAFE,
    ;

    /** HIDE: whether this field may be omitted without breaking the declared schema. */
    public val omittable: Boolean
        get() = this == TEXTUAL_OPTIONAL || this == NULLABLE

    /**
     * The [MaskingDecision] for a `REDACT` policy on this field, given its resolved [placeholder]:
     * the placeholder for a textual field, JSON null for a nullable non-textual field, or a fail-closed
     * [MaskingDecision.Omit] when no contract-safe value exists.
     */
    public fun redactDecision(placeholder: String): MaskingDecision =
        when (this) {
            TEXTUAL_REQUIRED, TEXTUAL_OPTIONAL -> MaskingDecision.WriteString(placeholder)
            NULLABLE -> MaskingDecision.WriteNull
            UNSAFE -> MaskingDecision.Omit(FailReason.REDACT_UNSAFE)
        }

    /**
     * Why [strategy] cannot be honoured on this field, or `null` if it can. The single predicate shared
     * by the runtime engine and the startup validator, so they agree by construction.
     */
    public fun unenforceableReason(strategy: MaskingStrategy): FailReason? =
        when (strategy) {
            MaskingStrategy.HIDE -> if (omittable) null else FailReason.HIDE_NON_NULLABLE
            MaskingStrategy.REDACT -> if (this == UNSAFE) FailReason.REDACT_UNSAFE else null
        }

    public companion object {
        /**
         * Classifies a field from the two neutral facts a backend can supply for any type system:
         * whether it is [isTextual] (masks to a string placeholder) and whether it is [nullable].
         */
        public fun of(
            isTextual: Boolean,
            nullable: Boolean,
        ): MaskingCapability =
            when {
                isTextual -> if (nullable) TEXTUAL_OPTIONAL else TEXTUAL_REQUIRED
                nullable -> NULLABLE
                else -> UNSAFE
            }
    }
}
