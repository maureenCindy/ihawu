package com.ihawu.core.policy

import com.ihawu.core.masking.MaskingStrategy

/**
 * A resolved masking decision for a single field - the output of policy resolution.
 *
 * @property field The name of the field or property the strategy applies to.
 * @property strategy How the field is masked (e.g. [MaskingStrategy.HIDE] or [MaskingStrategy.REDACT]).
 * @property placeholder The replacement value written when redacting. When null, the strategy's
 * [MaskingStrategy.defaultValue] is used instead.
 */
data class FieldPolicy(
    val field: String,
    val strategy: MaskingStrategy,
    val placeholder: String? = null,
)
