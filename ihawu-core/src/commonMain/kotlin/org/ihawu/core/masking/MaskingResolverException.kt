package org.ihawu.core.masking

/**
 * Thrown by [DefaultMaskingEngine] when the policy resolver fails and the engine is configured with
 * [ResolverErrorMode.FAIL_REQUEST], so a resolver outage surfaces as an error (an adapter maps it to a
 * `5xx`) instead of a silently masked `{}` that reports success.
 *
 * The [MaskingFailureSink] is notified with [FailReason.RESOLVER_ERROR] **before** this is thrown, so
 * `fail-request` is strictly more observable than `mask-all`, never less. See ADR 0011.
 *
 * @property resource the resource whose policy could not be resolved.
 */
class MaskingResolverException(
    val resource: String,
    override val cause: Throwable,
) : RuntimeException("Failed to resolve masking policy for resource '$resource'; failing the request", cause)
