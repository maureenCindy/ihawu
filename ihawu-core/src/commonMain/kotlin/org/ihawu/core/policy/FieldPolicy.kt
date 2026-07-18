package org.ihawu.core.policy

import org.ihawu.core.masking.MaskingStrategy

/**
 * A resolved masking decision for a single field - the output of policy resolution.
 *
 * @property field The name of the field or property the strategy applies to.
 * @property strategy How the field is masked (e.g. [MaskingStrategy.HIDE] or [MaskingStrategy.REDACT]).
 * @property placeholder The replacement written when redacting a **String** field; when null it falls
 * back to [MaskingStrategy.defaultPlaceholder]. Ignored for non-String fields, which mask to JSON null.
 */
data class FieldPolicy(
    val field: String,
    val strategy: MaskingStrategy,
    val placeholder: String? = null,
)
