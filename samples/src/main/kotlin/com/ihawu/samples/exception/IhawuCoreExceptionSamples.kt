package com.ihawu.samples.exception

import com.ihawu.core.exception.IhawuCoreException

fun throwWithMessage() {
    val exception = IhawuCoreException("Policy evaluation failed for resource 'UserProfile'")

    check(exception.message == "Policy evaluation failed for resource 'UserProfile'")
    check(exception.cause == null)
}

fun throwWithCause() {
    val rootCause = IllegalStateException("Connection to policy store timed out")
    val exception =
        IhawuCoreException(
            message = "Failed to fetch masking rules",
            cause = rootCause,
        )

    check(exception.message == "Failed to fetch masking rules")
    check(exception.cause === rootCause)
}
