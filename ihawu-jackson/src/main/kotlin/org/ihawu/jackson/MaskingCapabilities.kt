package org.ihawu.jackson

import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.SerializationConfig
import org.ihawu.core.masking.MaskingCapability
import kotlin.reflect.full.primaryConstructor

/**
 * Derives the [MaskingCapability] of every property of a resource bean from Jackson's introspection,
 * keyed by its **serialized** name.
 *
 * This is where the neutral [MaskingCapability] meets Jackson: it reads each property's declared type
 * (textual-or-not) and its Kotlin nullability, then hands those two facts to
 * [MaskingCapability.of]. The single source of truth shared by the two places that need it:
 * [IhawuBeanSerializerModifier], which wraps writers at serializer-build time, and
 * [MaskingContractValidator], which checks policies at startup — so a policy the validator accepts
 * behaves exactly as predicted at serialization time (including `@JsonProperty` renames).
 */
internal object MaskingCapabilities {
    /** Introspects [beanClass] via [config], then classifies each property. */
    fun of(
        config: SerializationConfig,
        beanClass: Class<*>,
    ): Map<String, MaskingCapability> = of(config.introspect(config.constructType(beanClass)))

    /** Classifies each property of an already-introspected [beanDesc]. */
    fun of(beanDesc: BeanDescription): Map<String, MaskingCapability> {
        // Kotlin nullability lives in @Metadata (JavaType erases it); read it from the primary-ctor
        // params, keyed by the logical property name. Unknown (Java class, body val, no primary ctor)
        // falls back to non-nullable.
        val nullableByProperty: Map<String, Boolean> =
            beanDesc.beanClass.kotlin.primaryConstructor
                ?.parameters
                ?.mapNotNull { p -> p.name?.let { it to p.type.isMarkedNullable } }
                ?.toMap()
                .orEmpty()

        return beanDesc.findProperties().associate { prop ->
            prop.name to
                MaskingCapability.of(
                    isTextual = prop.primaryType.isTypeOrSubTypeOf(CharSequence::class.java),
                    nullable = nullableByProperty[prop.internalName] ?: false,
                )
        }
    }
}
