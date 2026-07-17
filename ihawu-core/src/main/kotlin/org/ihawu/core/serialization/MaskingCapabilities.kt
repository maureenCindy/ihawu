package org.ihawu.core.serialization

import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.SerializationConfig
import kotlin.reflect.full.primaryConstructor

/**
 * Computes the [MaskingCapability] of every property of a resource bean, keyed by its **serialized**
 * name.
 *
 * The single source of truth shared by the two places that need it: [IhawuBeanSerializerModifier],
 * which wraps writers at serializer-build time, and [MaskingContractValidator], which checks policies
 * at startup. Both derive capabilities identically — from Jackson's introspection (so `@JsonProperty`
 * renames resolve the same way) and Kotlin nullability read from the primary constructor — so a policy
 * the validator accepts behaves exactly as predicted at serialization time.
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
            prop.name to MaskingCapability.of(prop.primaryType, nullableByProperty[prop.internalName] ?: false)
        }
    }
}
