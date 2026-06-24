package com.ihawu.core.serialization

import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import com.ihawu.core.annotation.IhawuResource
import com.ihawu.core.policy.ResourcePolicyResolver

/**
 * Wraps the property writers of every [IhawuResource]-annotated bean with a [MaskingPropertyWriter].
 *
 * Wrapping happens once per type while Jackson builds the serializer, so no reflection occurs on the
 * serialization hot path. Beans without [IhawuResource] are returned untouched. Nested annotated
 * types and collection elements are masked automatically, because each annotated type independently
 * receives wrapped writers — there is no manual graph walking.
 *
 * @property resolver Passed to each [MaskingPropertyWriter] to resolve per-request field policies.
 */
internal class IhawuBeanSerializerModifier(
    private val resolver: ResourcePolicyResolver,
) : BeanSerializerModifier() {
    override fun changeProperties(
        config: SerializationConfig,
        beanDesc: BeanDescription,
        beanProperties: MutableList<BeanPropertyWriter>,
    ): MutableList<BeanPropertyWriter> {
        val annotation =
            beanDesc.beanClass.getAnnotation(IhawuResource::class.java)
                ?: return beanProperties // not an Ihawu resource -> untouched
        return beanProperties.mapTo(mutableListOf()) {
            MaskingPropertyWriter(it, resolver, annotation.name)
        }
    }
}
