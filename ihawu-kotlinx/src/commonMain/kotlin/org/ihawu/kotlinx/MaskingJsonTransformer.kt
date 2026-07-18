package org.ihawu.kotlinx

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import org.ihawu.core.masking.MaskingCapability
import org.ihawu.core.masking.MaskingDecision
import org.ihawu.core.masking.MaskingEngine

/**
 * Masks an `@IhawuResource` type on kotlinx.serialization: the value serializes normally, then the
 * produced [JsonElement] is rewritten per the serialization-neutral [MaskingEngine] — `Pass` keeps the
 * entry, `Omit` drops the key, `WriteString`/`WriteNull` replace it. Capability comes straight from the
 * [SerialDescriptor] (a `PrimitiveKind.STRING` element is textual; `isNullable` marks a nullable field).
 *
 * kotlinx has no per-property hook, so recursion is manual, driven by [registry] (`serialName ->
 * resourceName`): nested `@IhawuResource` objects, list elements, and map values mask themselves as they
 * are reached. Polymorphic/sealed hierarchies are not handled in v1 (their elements pass through) —
 * their descriptor `serialName` is not a registry key. See ADR 0008.
 *
 * The cost vs the Jackson backend: this materialises the whole element tree before rewriting it.
 */
class MaskingJsonTransformer<T>(
    private val delegate: KSerializer<T>,
    private val engine: MaskingEngine,
    private val registry: Map<String, String>,
) : JsonTransformingSerializer<T>(delegate) {
    override fun transformSerialize(element: JsonElement): JsonElement = maskElement(element, delegate.descriptor)

    private fun maskElement(
        element: JsonElement,
        desc: SerialDescriptor,
    ): JsonElement =
        when {
            element is JsonObject && desc.kind == StructureKind.CLASS -> maskObject(element, desc)
            element is JsonObject && desc.kind == StructureKind.MAP ->
                JsonObject(element.mapValues { (_, v) -> maskElement(v, desc.getElementDescriptor(1)) })
            element is JsonArray && desc.kind == StructureKind.LIST ->
                JsonArray(element.map { maskElement(it, desc.getElementDescriptor(0)) })
            else -> element // primitives, JsonNull, or non-resource structures: unchanged
        }

    private fun maskObject(
        obj: JsonObject,
        desc: SerialDescriptor,
    ): JsonObject {
        // A nullable descriptor (e.g. an `Address?` field) reports serialName "Address?"; normalise it.
        val resource = registry[desc.serialName.removeSuffix("?")]
        val context = maskingContext ?: SimpleMaskingContext(null) // no context -> fail closed
        val out = LinkedHashMap<String, JsonElement>(obj.size)
        for ((name, value) in obj) {
            // Keys come from serializing this descriptor, so every name resolves to an element descriptor.
            val elemDesc = desc.getElementDescriptor(desc.getElementIndex(name))
            if (resource == null) {
                // not a masked resource: keep the field, but still recurse into any nested resources
                out[name] = maskElement(value, elemDesc)
                continue
            }
            val capability =
                MaskingCapability.of(
                    isTextual = elemDesc.kind == PrimitiveKind.STRING,
                    nullable = elemDesc.isNullable,
                )
            when (val decision = engine.decide(resource, name, capability, context)) {
                MaskingDecision.Pass -> out[name] = maskElement(value, elemDesc) // recurse into nested resources
                is MaskingDecision.Omit -> Unit // drop the field
                is MaskingDecision.WriteString -> out[name] = JsonPrimitive(decision.value)
                MaskingDecision.WriteNull -> out[name] = JsonNull
            }
        }
        return JsonObject(out)
    }
}
