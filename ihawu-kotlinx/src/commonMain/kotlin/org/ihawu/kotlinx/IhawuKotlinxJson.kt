package org.ihawu.kotlinx

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import org.ihawu.core.masking.MaskingEngine
import org.ihawu.core.policy.IhawuPrincipal

/**
 * Builds the resource registry ([serialName][kotlinx.serialization.descriptors.SerialDescriptor.serialName]
 * `-> resourceName`) that drives masking recursion, from each resource serializer and its `@IhawuResource`
 * name. Register every resource type — including nested ones and collection element types — so they mask
 * themselves when reached.
 */
fun maskingRegistry(vararg entries: Pair<KSerializer<*>, String>): Map<String, String> =
    entries.associate { (serializer, name) -> serializer.descriptor.serialName to name }

/** Wraps a resource serializer so it masks through [engine], recursing via [registry]. */
fun <T> maskingSerializer(
    delegate: KSerializer<T>,
    engine: MaskingEngine,
    registry: Map<String, String>,
): KSerializer<T> = MaskingJsonTransformer(delegate, engine, registry)

/** Encodes masked values with kotlinx.serialization, supplying the caller per encode. */
object IhawuKotlinxJson {
    /**
     * Encodes [value] with [serializer] (a [maskingSerializer]) for [principal], masking as it writes.
     * The per-call context is installed for the duration of this synchronous encode and cleared after,
     * so it never bleeds across calls; a `null` [principal] fails closed and the resource serializes as
     * `{}`. For coroutine callers (e.g. Ktor, #82) use `maskingContextElement(principal)` on the JVM
     * instead of this helper.
     */
    fun <T> encodeToString(
        json: Json,
        principal: IhawuPrincipal?,
        serializer: SerializationStrategy<T>,
        value: T,
    ): String {
        val previous = maskingContext
        maskingContext = SimpleMaskingContext(principal)
        return try {
            json.encodeToString(serializer, value)
        } finally {
            maskingContext = previous
        }
    }
}
