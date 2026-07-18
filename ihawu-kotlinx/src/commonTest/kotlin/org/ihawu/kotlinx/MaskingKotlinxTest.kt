package org.ihawu.kotlinx

import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
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

/** Sealed `@IhawuResource`: the spike proves its subtype masks with the discriminator preserved. */
@Serializable
sealed interface Shape {
    @Serializable
    @SerialName("circle")
    @IhawuResource("shape")
    data class Circle(
        val secret: String?,
    ) : Shape

    /** A second subtype that is deliberately NOT registered, to prove unregistered → passthrough. */
    @Serializable
    @SerialName("square")
    data class Square(
        val label: String,
    ) : Shape
}

/** A resource that holds a sealed `Shape` field, to prove nested polymorphic masking. */
@Serializable
@IhawuResource("gallery")
data class Gallery(
    val id: String,
    val shape: Shape,
)

/** OPEN polymorphic (abstract, not sealed): the spike proves it passes through unmasked (v1 follow-up). */
@Serializable
abstract class Animal {
    abstract val secret: String
}

@Serializable
@SerialName("dog")
@IhawuResource("animal")
data class Dog(
    override val secret: String,
) : Animal()

/** A second OPEN subtype registered on the Json module but NOT in the Ihawu registry: proves registry miss -> passthrough. */
@Serializable
@SerialName("cat")
data class Cat(
    override val secret: String,
) : Animal()

/** A resource that holds an OPEN `Animal` field, to prove nested OPEN polymorphic masking. */
@Serializable
@IhawuResource("zoo")
data class Zoo(
    val id: String,
    val resident: Animal,
)

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
            "animal" -> listOf(FieldPolicy("secret", MaskingStrategy.REDACT, "***")) // OPEN: proves it is NOT applied

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
    fun failsClosedWhenNoContextInstalled() {
        // Encoding directly (not via IhawuKotlinxJson.encodeToString) leaves no maskingContext -> fallback fails closed.
        val previous = maskingContext
        maskingContext = null
        try {
            val out = json.parseToJsonElement(json.encodeToString(employeeSer, sample)).jsonObject
            assertTrue(out.isEmpty()) // no installed context -> {}
        } finally {
            maskingContext = previous
        }
    }

    private val shapeRegistry = maskingRegistry(Shape.Circle.serializer() to "shape")

    @Test
    fun sealedSubtypeMasksAndPreservesDiscriminator() {
        val shapeSer = maskingSerializer(Shape.serializer(), engine, shapeRegistry)
        val out = json.parseToJsonElement(IhawuKotlinxJson.encodeToString(json, principal, shapeSer, Shape.Circle("top-secret"))).jsonObject
        assertEquals("circle", out["type"]?.jsonPrimitive?.content) // discriminator preserved
        assertEquals("***", out["secret"]?.jsonPrimitive?.content) // REDACT applied
    }

    @Test
    fun sealedSubtypeMasksNestedInsideAnotherResource() {
        val galleryRegistry = maskingRegistry(Gallery.serializer() to "gallery", Shape.Circle.serializer() to "shape")
        val galSer = maskingSerializer(Gallery.serializer(), engine, galleryRegistry)
        val value = Gallery("g1", Shape.Circle("top-secret"))
        val out = json.parseToJsonElement(IhawuKotlinxJson.encodeToString(json, principal, galSer, value)).jsonObject
        assertEquals("g1", out["id"]?.jsonPrimitive?.content)
        val shape = out["shape"]!!.jsonObject
        assertEquals("circle", shape["type"]?.jsonPrimitive?.content) // discriminator preserved
        assertEquals("***", shape["secret"]?.jsonPrimitive?.content) // nested subtype masked
    }

    @Test
    fun sealedSubtypeMasksInsideList() {
        val listSer = maskingSerializer(ListSerializer(Shape.serializer()), engine, shapeRegistry)
        val value = listOf<Shape>(Shape.Circle("a"), Shape.Circle("b"))
        val out = json.parseToJsonElement(IhawuKotlinxJson.encodeToString(json, principal, listSer, value)).jsonArray
        assertEquals(2, out.size)
        out.forEach {
            assertEquals("circle", it.jsonObject["type"]?.jsonPrimitive?.content)
            assertEquals("***", it.jsonObject["secret"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun sealedSubtypeMasksWithArrayPolymorphism() {
        val arrJson = Json { useArrayPolymorphism = true }
        val shapeSer = maskingSerializer(Shape.serializer(), engine, shapeRegistry)
        val encoded = IhawuKotlinxJson.encodeToString(arrJson, principal, shapeSer, Shape.Circle("top-secret"))
        val out = arrJson.parseToJsonElement(encoded).jsonArray
        assertEquals(2, out.size)
        assertEquals("circle", out[0].jsonPrimitive.content) // discriminator (element[0]) preserved
        assertEquals("***", out[1].jsonObject["secret"]?.jsonPrimitive?.content) // payload (element[1]) masked
    }

    @Test
    fun sealedSubtypeMasksWithCustomDiscriminatorKey() {
        // Proves threading a non-default classDiscriminator through the factory + transformer.
        val customJson = Json { classDiscriminator = "kind" }
        val shapeSer = maskingSerializer(Shape.serializer(), engine, shapeRegistry, classDiscriminator = "kind")
        val encoded = IhawuKotlinxJson.encodeToString(customJson, principal, shapeSer, Shape.Circle("top-secret"))
        val out = customJson.parseToJsonElement(encoded).jsonObject
        assertEquals("circle", out["kind"]?.jsonPrimitive?.content) // custom discriminator preserved
        assertEquals("***", out["secret"]?.jsonPrimitive?.content)
    }

    @Test
    fun unregisteredSealedSubtypePassesThroughUnmasked() {
        // Square is not in the registry: it must pass through unmasked, discriminator intact.
        val shapeSer = maskingSerializer(Shape.serializer(), engine, shapeRegistry)
        val out = json.parseToJsonElement(IhawuKotlinxJson.encodeToString(json, principal, shapeSer, Shape.Square("keep-me"))).jsonObject
        assertEquals("square", out["type"]?.jsonPrimitive?.content)
        assertEquals("keep-me", out["label"]?.jsonPrimitive?.content) // untouched
    }

    @Test
    fun mismatchedDiscriminatorKeyPassesThrough() {
        // Transformer configured for "wrong" but Json emits "type": no discriminator found -> passthrough.
        val shapeSer = maskingSerializer(Shape.serializer(), engine, shapeRegistry, classDiscriminator = "wrong")
        val out = json.parseToJsonElement(IhawuKotlinxJson.encodeToString(json, principal, shapeSer, Shape.Circle("top-secret"))).jsonObject
        assertEquals("circle", out["type"]?.jsonPrimitive?.content)
        assertEquals("top-secret", out["secret"]?.jsonPrimitive?.content) // NOT masked: discriminator not located
    }

    // Cat is registered on the Json module but NOT in animalRegistry -> proves registry-miss passthrough.
    private val openModule =
        SerializersModule {
            polymorphic(Animal::class) {
                subclass(Dog::class)
                subclass(Cat::class)
            }
        }
    private val animalRegistry = maskingRegistry(Dog.serializer() to "animal")

    @Test
    fun openPolymorphicSubtypeMasksAndPreservesDiscriminator() {
        // Spike #104: OPEN (abstract) subtype masked via serializersModule.getPolymorphic + capturedKClass.
        val openJson = Json { serializersModule = openModule }
        val animalSer = maskingSerializer(PolymorphicSerializer(Animal::class), engine, animalRegistry, serializersModule = openModule)
        val out = openJson.parseToJsonElement(IhawuKotlinxJson.encodeToString(openJson, principal, animalSer, Dog("top-secret"))).jsonObject
        assertEquals("dog", out["type"]?.jsonPrimitive?.content) // discriminator preserved
        assertEquals("***", out["secret"]?.jsonPrimitive?.content) // REDACT applied
    }

    @Test
    fun openPolymorphicSubtypeMasksNestedInsideAnotherResource() {
        val openJson = Json { serializersModule = openModule }
        val zooRegistry = maskingRegistry(Zoo.serializer() to "zoo", Dog.serializer() to "animal")
        val zooSer = maskingSerializer(Zoo.serializer(), engine, zooRegistry, serializersModule = openModule)
        val out =
            openJson
                .parseToJsonElement(
                    IhawuKotlinxJson.encodeToString(openJson, principal, zooSer, Zoo("z1", Dog("top-secret"))),
                ).jsonObject
        assertEquals("z1", out["id"]?.jsonPrimitive?.content)
        val resident = out["resident"]!!.jsonObject
        assertEquals("dog", resident["type"]?.jsonPrimitive?.content) // discriminator preserved
        assertEquals("***", resident["secret"]?.jsonPrimitive?.content) // nested OPEN subtype masked
    }

    @Test
    fun openPolymorphicSubtypeMasksInsideList() {
        val openJson = Json { serializersModule = openModule }
        val listSer =
            maskingSerializer(ListSerializer(PolymorphicSerializer(Animal::class)), engine, animalRegistry, serializersModule = openModule)
        val value = listOf<Animal>(Dog("a"), Dog("b"))
        val out = openJson.parseToJsonElement(IhawuKotlinxJson.encodeToString(openJson, principal, listSer, value)).jsonArray
        assertEquals(2, out.size)
        out.forEach {
            assertEquals("dog", it.jsonObject["type"]?.jsonPrimitive?.content)
            assertEquals("***", it.jsonObject["secret"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun openPolymorphicSubtypeMasksWithArrayPolymorphism() {
        val openArrJson =
            Json {
                serializersModule = openModule
                useArrayPolymorphism = true
            }
        val animalSer = maskingSerializer(PolymorphicSerializer(Animal::class), engine, animalRegistry, serializersModule = openModule)
        val encoded = IhawuKotlinxJson.encodeToString(openArrJson, principal, animalSer, Dog("top-secret"))
        val out = openArrJson.parseToJsonElement(encoded).jsonArray
        assertEquals("dog", out[0].jsonPrimitive.content) // discriminator (element[0]) preserved
        assertEquals("***", out[1].jsonObject["secret"]?.jsonPrimitive?.content) // payload (element[1]) masked
    }

    @Test
    fun unregisteredOpenSubtypePassesThroughUnmasked() {
        // Cat resolves via the module (so it serializes) but is absent from animalRegistry -> not masked.
        val openJson = Json { serializersModule = openModule }
        val animalSer = maskingSerializer(PolymorphicSerializer(Animal::class), engine, animalRegistry, serializersModule = openModule)
        val out = openJson.parseToJsonElement(IhawuKotlinxJson.encodeToString(openJson, principal, animalSer, Cat("keep-me"))).jsonObject
        assertEquals("cat", out["type"]?.jsonPrimitive?.content)
        assertEquals("keep-me", out["secret"]?.jsonPrimitive?.content) // registry miss -> untouched
    }

    @Test
    fun openPolymorphicArrayWithoutModulePassesThroughUnmasked() {
        // Array-polymorphism encoding, but the transformer has no module -> OPEN subtype unresolved -> passthrough.
        val openArrJson =
            Json {
                serializersModule = openModule
                useArrayPolymorphism = true
            }
        val animalSer = maskingSerializer(PolymorphicSerializer(Animal::class), engine, animalRegistry) // no module
        val encoded = IhawuKotlinxJson.encodeToString(openArrJson, principal, animalSer, Dog("top-secret"))
        val out = openArrJson.parseToJsonElement(encoded).jsonArray
        assertEquals("dog", out[0].jsonPrimitive.content)
        assertEquals("top-secret", out[1].jsonObject["secret"]?.jsonPrimitive?.content) // no module -> unmasked
    }

    @Test
    fun openPolymorphicWithoutModulePassesThroughUnmasked() {
        // No serializersModule threaded into the transformer (default empty) -> OPEN subtype cannot be resolved.
        val openJson = Json { serializersModule = openModule }
        val animalSer = maskingSerializer(PolymorphicSerializer(Animal::class), engine, animalRegistry) // no module
        val out = openJson.parseToJsonElement(IhawuKotlinxJson.encodeToString(openJson, principal, animalSer, Dog("top-secret"))).jsonObject
        assertEquals("dog", out["type"]?.jsonPrimitive?.content)
        assertEquals("top-secret", out["secret"]?.jsonPrimitive?.content) // no module -> unmasked
    }
}
