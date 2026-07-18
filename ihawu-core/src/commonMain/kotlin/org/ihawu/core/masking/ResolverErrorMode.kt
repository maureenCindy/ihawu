package org.ihawu.core.masking

/**
 * How [DefaultMaskingEngine] reacts when the policy resolver throws — a policy-store outage or a
 * misconfiguration. Scoped to the resolver-error path only: a missing principal always masks fail-closed
 * regardless of this mode. See [ADR 0011](../../../../../../../docs/adr/0011-configurable-fail-request-on-resolver-error.md).
 */
enum class ResolverErrorMode {
    /** Mask the whole resource fail-closed (`{}`) and continue — the safe default; the response stays 200. */
    MASK_ALL,

    /**
     * Notify the failure sink, then throw [MaskingResolverException] so the request fails (e.g. a 5xx),
     * turning a silent outage into an alertable signal. Best-effort — see the committed-response caveat
     * in ADR 0011.
     */
    FAIL_REQUEST,
}
