package org.ihawu.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.masking.DefaultMaskingEngine
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** A plain bean — no [IhawuResource], so the modifier must leave its writers untouched. */
private data class User(
    val name: String,
    val age: Int,
)

@IhawuResource("account")
private data class Account(
    val id: String,
    val balance: Int,
)

private class DummyResolver : ResourcePolicyResolver {
    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> = emptyList()
}

class IhawuBeanSerializerModifierTest {
    private val mapper = ObjectMapper()
    private val modifier = IhawuBeanSerializerModifier(DefaultMaskingEngine(DummyResolver()))

    /** Borrows a real SerializationConfig + BeanDescription from Jackson for [type]. */
    private fun changeProperties(
        type: Class<*>,
        properties: MutableList<BeanPropertyWriter>,
    ): MutableList<BeanPropertyWriter> {
        val config = mapper.serializationConfig
        val beanDesc = config.introspect(mapper.constructType(type))
        return modifier.changeProperties(config, beanDesc, properties)
    }

    @Test
    fun `returns the property writers untouched for a non-IhawuResource bean`() {
        val original = mutableListOf<BeanPropertyWriter>()

        val result = changeProperties(User::class.java, original)

        assertSame(original, result) // exact same list back -> no MaskingPropertyWriter was created
    }

    @Test
    fun `replaces the property writers for an IhawuResource bean`() {
        val original = mutableListOf<BeanPropertyWriter>()

        val result = changeProperties(Account::class.java, original)

        assertNotSame(original, result) // a new list -> the wrapping branch was taken
    }
}
