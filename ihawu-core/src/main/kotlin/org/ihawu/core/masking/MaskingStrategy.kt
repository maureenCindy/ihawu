package org.ihawu.core.masking

/**
 * Defines how Ihawu transforms a restricted field before it reaches the serialization output.
 *
 * Each strategy represents a different level of data protection applied at the
 * serialization boundary. The [securityLevel] allows policies to compare and
 * escalate strategies — a higher value means stricter enforcement.
 *
 * @property description A human-readable explanation of the strategy behavior.
 * @property securityLevel Numeric rank used for comparison. Higher values indicate
 *   stricter protection (e.g. [HIDE] > [REDACT]).
 * @property defaultPlaceholder The fallback *string* placeholder for REDACT when a FieldPolicy gives none. Non-String fields
 * never use this — their masked form is JSON null, decided by [MaskingCapability].
 */
enum class MaskingStrategy(
    val description: String,
    val securityLevel: Int,
    val defaultPlaceholder: String? = null,
) {
    /** Removes the field entirely from the serialized output. */
    HIDE(
        description = "Removes the field entirely from the output",
        securityLevel = 2,
    ),

    /** Replaces the field value with an obfuscated placeholder. */
    REDACT(
        description = "Replaces the field value with an obfuscated placeholder",
        securityLevel = 1,
        defaultPlaceholder = "***-**-****",
    ),
}
