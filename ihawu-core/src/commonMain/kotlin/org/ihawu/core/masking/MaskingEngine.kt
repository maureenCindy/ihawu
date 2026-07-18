package org.ihawu.core.masking

/**
 * The serialization-neutral masking contract: given one field, decide what to write.
 *
 * This is the seam every backend builds on. A backend calls [decide] per field — however it can reach
 * one (Jackson wraps each property writer; a backend without a per-property hook may drive it during a
 * tree rewrite or a custom encoder) — and executes the returned [MaskingDecision]. The engine holds all
 * the value: policy resolution, per-call memoization, fail-closed behaviour, and the type contract.
 * Recursion into nested resources and collection elements is the backend's concern, not the engine's.
 */
interface MaskingEngine {
    /**
     * @param resource the `@IhawuResource` name being serialized.
     * @param field the serialized field name.
     * @param capability how [field] may be masked, derived from its declared type (see [MaskingCapability]).
     * @param context the call-scoped context carrying the principal and per-call memoization.
     */
    fun decide(
        resource: String,
        field: String,
        capability: MaskingCapability,
        context: MaskingContext,
    ): MaskingDecision
}
