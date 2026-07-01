package com.ihawu.samples.masking

import com.ihawu.core.masking.MaskingStrategy

fun hideRemovesField() {
    val strategy = MaskingStrategy.HIDE

    check(strategy.defaultValue == null)
    check(strategy.securityLevel > MaskingStrategy.REDACT.securityLevel)
}

fun redactReplacesValue() {
    val strategy = MaskingStrategy.REDACT

    val placeholder = strategy.defaultValue?.invoke()
    check(placeholder == "***-**-****")
}
