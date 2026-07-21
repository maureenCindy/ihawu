package org.ihawu.core.masking

/**
 * Notified whenever a [MaskingEngine] drops a field **fail-closed**, so a serialization backend can
 * surface the failure without the engine depending on any logging library.
 *
 * This is the minimal seam that keeps the engine serialization- and logging-neutral: the engine owns
 * the fail-closed decision; the backend (e.g. the Jackson module) supplies a sink that logs. A richer
 * failure-listener with metrics and a configurable fail-request mode is planned to build on this seam.
 *
 * Resource-level failures ([FailReason.NO_PRINCIPAL], [FailReason.RESOLVER_ERROR]) fire once per
 * (call, resource) with a `null` [field]; per-field failures fire with the field name.
 */
public fun interface MaskingFailureSink {
    public fun onFailClosed(
        resource: String,
        field: String?,
        reason: FailReason,
        cause: Throwable?,
    )
}
