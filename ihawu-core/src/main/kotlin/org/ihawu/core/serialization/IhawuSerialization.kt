package org.ihawu.core.serialization

/**
 * Keys for the per-call Jackson attributes that carry request context into Ihawu's masking
 * serializer.
 *
 * The host framework attaches the authenticated identity to a single serialization call via
 * `ObjectWriter.withAttribute`; the masking serializer reads it back through
 * `SerializerProvider.getAttribute`. Scoping the principal to one write call — rather than a
 * thread-bound holder — makes cross-request contamination structurally impossible. See
 * `docs/adr/0001-serialization-context-passing.md`.
 */
object IhawuSerialization {
    /**
     * Attribute key under which the current [org.ihawu.core.policy.IhawuPrincipal] is supplied for
     * the serialization call. When absent, the masking serializer fails closed and the resource
     * serializes as an empty object rather than leaking unmasked data.
     */
    const val PRINCIPAL: String = "org.ihawu.principal"
}
