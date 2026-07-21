package org.ihawu.core.masking

/**
 * The event fired for each fail-closed drop, delivered to [MaskingFailureSink.onFailClosed].
 *
 * Resource-level failures ([FailReason.NO_PRINCIPAL], [FailReason.RESOLVER_ERROR]) carry a `null`
 * [field]; per-field failures carry the field name. The event never carries the protected value.
 *
 * Deliberately a plain class, **not** a data class: Ihawu is the only constructor of events, so new
 * properties can be added later without breaking any consumer — a data class's `copy`/`componentN`
 * would freeze the shape and defeat that extensibility.
 *
 * @property resource The `@IhawuResource` name the failure occurred on.
 * @property reason Why the engine failed closed.
 * @property field The field that was dropped, or `null` for a resource-level failure.
 * @property cause The underlying exception, when one triggered the failure (e.g. a resolver error).
 */
public class MaskingFailure(
    public val resource: String,
    public val reason: FailReason,
    public val field: String? = null,
    public val cause: Throwable? = null,
) {
    override fun toString(): String = "MaskingFailure(resource=$resource, reason=$reason, field=$field)"
}
