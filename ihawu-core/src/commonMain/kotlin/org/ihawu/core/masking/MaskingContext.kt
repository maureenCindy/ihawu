package org.ihawu.core.masking

import org.ihawu.core.policy.IhawuPrincipal

/**
 * The call-scoped context a serialization backend supplies to a [MaskingEngine] for one write.
 *
 * It replaces the engine's reliance on any serialization library's own per-call state — on Jackson,
 * for instance, this is backed by `SerializerProvider` attributes. Scoping to a single write call
 * rather than a thread-bound holder makes cross-request contamination structurally impossible.
 */
public interface MaskingContext {
    /** The caller this write is for, or `null` — which masks the whole resource fail-closed. */
    public val principal: IhawuPrincipal?

    /**
     * Returns the value stored under [key] for this call, computing and caching it with [compute] on
     * first access. Lets the engine resolve each resource's policy at most once per call, however many
     * instances of that resource the write contains.
     */
    public fun <T : Any> memoize(
        key: String,
        compute: () -> T,
    ): T
}
