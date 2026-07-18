package org.ihawu.core.serialization

import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.masking.MaskingCapability
import org.ihawu.core.masking.MaskingEngine

/**
 * Wraps the property writers of every [IhawuResource]-annotated bean with a [MaskingPropertyWriter].
 *
 * Wrapping happens once per type while Jackson builds the serializer, so no reflection occurs on the
 * serialization hot path: each field's [MaskingCapability] is derived here, once, and the per-request
 * decision is delegated to the [engine] at write time. Beans without [IhawuResource] are returned
 * untouched. Nested annotated types and collection elements are masked automatically, because each
 * annotated type independently receives wrapped writers — there is no manual graph walking.
 *
 * @property engine The serialization-neutral engine each [MaskingPropertyWriter] decides through.
 */
internal class IhawuBeanSerializerModifier(
    private val engine: MaskingEngine,
) : BeanSerializerModifier() {
    override fun changeProperties(
        config: SerializationConfig,
        beanDesc: BeanDescription,
        beanProperties: MutableList<BeanPropertyWriter>,
    ): MutableList<BeanPropertyWriter> {
        val annotation =
            beanDesc.beanClass.getAnnotation(IhawuResource::class.java)
                ?: return beanProperties // not an Ihawu resource -> untouched

        // Capabilities computed once per type here; the same helper backs the startup validator, so a
        // policy it accepts behaves exactly as predicted at serialization time.
        val capabilities = MaskingCapabilities.of(beanDesc)

        return beanProperties.mapTo(mutableListOf()) {
            val capability =
                capabilities[it.name]
                    ?: MaskingCapability.of(
                        isTextual = it.type.isTypeOrSubTypeOf(CharSequence::class.java),
                        nullable = false,
                    )
            MaskingPropertyWriter(it, engine, annotation.name, capability)
        }
    }
}
