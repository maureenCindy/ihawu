package org.ihawu.core.masking

/**
 * Defines how Ihawu transforms a restricted field before it reaches the serialization output.
 *
 * Each strategy represents a different level of data protection applied at the
 * serialization boundary. Strategies are ranked internally so policies can compare and
 * escalate them — when two rules mask the same field, the stricter strategy wins
 * (e.g. [HIDE] outranks [REDACT]).
 *
 * @property defaultPlaceholder The fallback *string* placeholder for REDACT when a FieldPolicy gives none. Non-String fields
 * never use this — their masked form is JSON null, decided by [MaskingCapability].
 */
public enum class MaskingStrategy(
    internal val securityLevel: Int,
    public val defaultPlaceholder: String? = null,
) {
    /** Removes the field entirely from the serialized output. */
    HIDE(
        securityLevel = 2,
    ),

    /** Replaces the field value with an obfuscated placeholder. */
    REDACT(
        securityLevel = 1,
        defaultPlaceholder = "***-**-****",
    ),
}
