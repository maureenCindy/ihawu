package org.ihawu.kotlinx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.masking.DefaultMaskingEngine
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
@IhawuResource("employee")
data class Employee(
    val id: String,
    val ssn: String?, // REDACT -> "***"
    val salary: Int?, // HIDE -> dropped
    val bonus: Int?, // REDACT -> null (nullable non-String)
    val address: Address?, // nested resource (nullable -> serialName "Address?")
    val note: Note? = null, // nested NON-resource -> passthrough
    val reports: Map<String, Address> = emptyMap(), // MAP of resources -> values masked
)

@Serializable
@IhawuResource("address")
data class Address(
    val city: String,
    val zip: String?, // HIDE -> dropped
)

/** Not an `@IhawuResource` / not registered: proves non-resource nested objects pass through. */
@Serializable
data class Note(
    val text: String,
)

/** Polymorphic/sealed: out of scope in v1 — proves it passes through unmasked (documented limitation). */
@Serializable
sealed interface Shape {
    @Serializable
    @SerialName("circle")
    @IhawuResource("shape")
    data class Circle(
        val secret: String?,
    ) : Shape
}

internal class TestResolver : ResourcePolicyResolver {
    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> =
        when (resource) {
            "employee" ->
                listOf(
                    FieldPolicy("ssn", MaskingStrategy.REDACT, "***"),
                    FieldPolicy("salary", MaskingStrategy.HIDE),
                    FieldPolicy("bonus", MaskingStrategy.REDACT),
                )
            "address" -> listOf(FieldPolicy("zip", MaskingStrategy.HIDE))
            "shape" -> listOf(FieldPolicy("secret", MaskingStrategy.REDACT, "***"))
            else -> emptyList()
        }
}

internal val sample =
    Employee(
        id = "e1",
        ssn = "123-45-6789",
        salary = 90_000,
        bonus = 5_000,
        address = Address("Harare", "00263"),
        note = Note("keep me"),
        reports = mapOf("r1" to Address("Bulawayo", "00264")),
    )

class MaskingKotlinxTest {
    private val json = Json
    private val engine = DefaultMaskingEngine(TestResolver())
    private val registry = maskingRegistry(Employee.serializer() to "employee", Address.serializer() to "address")
    private val employeeSer = maskingSerializer(Employee.serializer(), engine, registry)
    private val principal = IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())

    private fun encode() = json.parseToJsonElement(IhawuKotlinxJson.encodeToString(json, principal, employeeSer, sample)).jsonObject

    @Test
    fun masksFlatFieldsPerStrategyAndType() {
        val out = encode()
        assertEquals("e1", out["id"]?.jsonPrimitive?.content) // no policy -> passthrough
        assertEquals("***", out["ssn"]?.jsonPrimitive?.content) // REDACT String -> placeholder
        assertFalse("salary" in out) // HIDE nullable -> dropped
        assertTrue(out["bonus"] is JsonNull) // REDACT nullable non-String -> null
    }

    @Test
    fun masksNestedResourceThroughNullableField() {
        // address is `Address?` (serialName "Address?"): the registry lookup must normalise the trailing '?'.
        val addr = encode()["address"]!!.jsonObject
        assertEquals("Harare", addr["city"]?.jsonPrimitive?.content)
        assertFalse("zip" in addr) // nested resource HIDE -> dropped
    }

    @Test
    fun passesThroughNestedNonResource() {
        val note = encode()["note"]!!.jsonObject
        assertEquals("keep me", note["text"]?.jsonPrimitive?.content) // not registered -> untouched
    }

    @Test
    fun masksMapValues() {
        val reports = encode()["reports"]!!.jsonObject
        val r1 = reports["r1"]!!.jsonObject
        assertEquals("Bulawayo", r1["city"]?.jsonPrimitive?.content)
        assertFalse("zip" in r1) // map value is a resource -> masked
    }

    @Test
    fun masksCollectionElements() {
        val listSer = maskingSerializer(ListSerializer(Employee.serializer()), engine, registry)
        val out = json.parseToJsonElement(IhawuKotlinxJson.encodeToString(json, principal, listSer, listOf(sample, sample))).jsonArray
        assertEquals(2, out.size)
        out.forEach { assertEquals("***", it.jsonObject["ssn"]?.jsonPrimitive?.content) }
    }

    @Test
    fun failsClosedWithoutPrincipal() {
        val out = json.parseToJsonElement(IhawuKotlinxJson.encodeToString(json, null, employeeSer, sample)).jsonObject
        assertTrue(out.isEmpty()) // no principal -> {}
    }

    @Test
    fun polymorphicResourceIsNotMaskedInV1() {
        // Documented v1 limitation: a sealed/polymorphic @IhawuResource passes through unmasked.
        val shapeSer = maskingSerializer(Shape.serializer(), engine, maskingRegistry(Shape.Circle.serializer() to "shape"))
        val out = IhawuKotlinxJson.encodeToString(json, principal, shapeSer, Shape.Circle("top-secret"))
        assertTrue(out.contains("top-secret")) // NOT masked — polymorphism is a follow-up
    }
}
