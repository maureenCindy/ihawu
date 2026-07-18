package org.ihawu.kotlinx

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.capturedKClass
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
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
 * are reached. **Sealed** `@IhawuResource` subtypes are masked too — the [classDiscriminator] entry is
 * preserved and the payload masked against the concrete subtype descriptor, for both the object and the
 * array-polymorphism encodings (ADR 0010). **OPEN (non-sealed abstract/interface) hierarchies** are masked
 * too: their subtypes are not descriptor-enumerable, so the concrete subtype descriptor is resolved from
 * [serializersModule] via `getPolymorphic(baseClass, discriminatorValue)`, with the base `KClass` read off
 * the `PolymorphicKind.OPEN` descriptor's `capturedKClass` (works top-level AND nested, on both JVM and
 * Kotlin/JS). OPEN requires the caller to register subtypes on the encoding `Json`'s module. See ADR 0010.
 *
 * The cost vs the Jackson backend: this materialises the whole element tree before rewriting it.
 */
class MaskingJsonTransformer<T>(
    private val delegate: KSerializer<T>,
    private val engine: MaskingEngine,
    private val registry: Map<String, String>,
    private val classDiscriminator: String = "type",
    private val serializersModule: SerializersModule = EmptySerializersModule(),
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
            // Polymorphic default encoding: {"<disc>":"circle", ...payload}. Mask payload, keep discriminator.
            element is JsonObject && desc.kind is PolymorphicKind -> maskPolymorphicObject(element, desc)
            // Array polymorphism: ["circle", {...payload}]. Mask element[1], keep element[0].
            element is JsonArray && desc.kind is PolymorphicKind -> maskPolymorphicArray(element, desc)
            else -> element // primitives, JsonNull, or non-resource structures: unchanged
        }

    /**
     * Resolves the concrete subtype descriptor from a discriminator value, for a SEALED base only.
     * Spike-confirmed shape (kotlinx 1.8.1): a SEALED descriptor has elementsCount=2 — element[0] "type"
     * (String) and element[1] "value" (CONTEXTUAL, serialName `kotlinx.serialization.Sealed<Base>`) whose
     * child element descriptors are the concrete subtypes, each with `serialName == @SerialName`
     * (e.g. "circle") — which is exactly the discriminator value. No SerializersModule needed.
     * OPEN/abstract bases are not enumerable this way, so this returns null → passthrough.
     */
    private fun resolveSealedSubtype(
        desc: SerialDescriptor,
        discriminatorValue: String,
    ): SerialDescriptor? {
        if (desc.kind !is PolymorphicKind.SEALED) return null
        val valueSlot = desc.getElementDescriptor(1) // the "value" slot
        for (i in 0 until valueSlot.elementsCount) {
            val sub = valueSlot.getElementDescriptor(i)
            if (sub.serialName == discriminatorValue) return sub
        }
        return null
    }

    /**
     * Resolves the concrete subtype descriptor for an OPEN (non-sealed abstract/interface) base.
     * Spike #104-confirmed shape (kotlinx 1.8.1, JVM + Kotlin/JS): a `PolymorphicKind.OPEN` descriptor
     * exposes the base `KClass` via [capturedKClass] (both top-level and nested-in-another-resource), and
     * the registered subtype descriptor comes from the module: `getPolymorphic(baseClass, discriminator)`
     * — where `discriminator` is the registered subtype name (e.g. "dog"), and the returned descriptor's
     * `serialName` is that same name (which is what the [registry] is keyed on). Requires the app to have
     * registered the subtype on the encoding `Json`'s `serializersModule`; an unregistered subtype (or an
     * empty module) yields null → passthrough.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun resolveOpenSubtype(
        desc: SerialDescriptor,
        discriminatorValue: String,
    ): SerialDescriptor? {
        // Reached only for a PolymorphicKind.OPEN descriptor (sealed is handled first), whose capturedKClass
        // is the base type on both JVM and JS (spike #104). An empty/mismatched module yields null -> passthrough.
        val baseClass = desc.capturedKClass ?: return null
        return serializersModule.getPolymorphic(baseClass, serializedClassName = discriminatorValue)?.descriptor
    }

    /** Sealed enumeration first, then the OPEN module lookup; null → not a known subtype → passthrough. */
    private fun resolveSubtype(
        desc: SerialDescriptor,
        discriminatorValue: String,
    ): SerialDescriptor? =
        resolveSealedSubtype(desc, discriminatorValue) ?: resolveOpenSubtype(desc, discriminatorValue)

    private fun maskPolymorphicObject(
        obj: JsonObject,
        desc: SerialDescriptor,
    ): JsonElement {
        // Absent only if `classDiscriminator` is misconfigured vs the encoding Json's key -> passthrough.
        val discriminator = (obj[classDiscriminator] as? JsonPrimitive)?.contentOrNull ?: return obj
        val subtype = resolveSubtype(desc, discriminator) ?: return obj // unknown/unregistered subtype -> passthrough
        // The discriminator key is NOT an element of the subtype descriptor; copy it verbatim, mask the rest.
        return maskObject(obj, subtype, passthroughKey = classDiscriminator)
    }

    private fun maskPolymorphicArray(
        arr: JsonArray,
        desc: SerialDescriptor,
    ): JsonElement {
        // Array polymorphism always emits exactly [discriminatorString, payloadObject] for a class subtype.
        val discriminator = (arr[0] as JsonPrimitive).content
        val subtype = resolveSubtype(desc, discriminator) ?: return arr // unknown/unregistered subtype -> passthrough
        return JsonArray(listOf(arr[0], maskObject(arr[1] as JsonObject, subtype)))
    }

    private fun maskObject(
        obj: JsonObject,
        desc: SerialDescriptor,
        passthroughKey: String? = null,
    ): JsonObject {
        // A nullable descriptor (e.g. an `Address?` field) reports serialName "Address?"; normalise it.
        val resource = registry[desc.serialName.removeSuffix("?")]
        val context = maskingContext ?: SimpleMaskingContext(null) // no context -> fail closed
        val out = LinkedHashMap<String, JsonElement>(obj.size)
        for ((name, value) in obj) {
            if (name == passthroughKey) {
                // The polymorphic discriminator: not an element of this descriptor; copy through verbatim.
                out[name] = value
                continue
            }
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
