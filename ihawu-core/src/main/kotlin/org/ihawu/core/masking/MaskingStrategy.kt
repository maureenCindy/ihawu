package org.ihawu.core.masking

/**
 * Defines how Ihawu transforms a restricted field before it reaches the serialization output.
 *
 * Each strategy represents a different level of data protection applied at the
 * serialization boundary. The [securityLevel] allows policies to compare and
 * escalate strategies — a higher value means stricter enforcement.
 *
 * @property description A human-readable explanation of the strategy behaviour.
 * @property securityLevel Numeric rank used for comparison. Higher values indicate
 *   stricter protection (e.g. [HIDE] > [REDACT]).
 * @property defaultValue An optional factory that produces the replacement value
 *   written to the output stream. Returns `null` for strategies that remove the
 *   field entirely rather than substituting a placeholder.
 *
 * @sample org.ihawu.samples.masking.hideRemovesField
 * @sample org.ihawu.samples.masking.redactReplacesValue
 */
enum class MaskingStrategy(
    val description: String,
    val securityLevel: Int,
    val defaultValue: (() -> String)? = null,
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
        defaultValue = { "***-**-****" },
    ),
}
