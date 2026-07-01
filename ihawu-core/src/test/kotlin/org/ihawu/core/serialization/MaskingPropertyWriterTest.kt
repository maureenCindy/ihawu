package org.ihawu.core.serialization

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.common.LogRecorder
import org.ihawu.core.exception.IhawuCoreException
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver
import org.slf4j.event.Level
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@IhawuResource("promotion")
private data class Promotion(
    val startDate: String,
    val position: String,
    val salary: Double,
)

@IhawuResource("employee")
private data class Employee(
    val name: String,
    val ssn: String,
    val promotions: List<Promotion>,
)

private class CustomPolicyResolver : ResourcePolicyResolver {
    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> =
        if (resource == "employee") {
            listOf(FieldPolicy("ssn", MaskingStrategy.REDACT, "***ssn"))
        } else {
            listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "$***"))
        }
}

/** Resolver that always blows up — simulates a misconfig / policy-store outage. */
private class ThrowingResolver : ResourcePolicyResolver {
    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> = throw IhawuCoreException("boom for $resource")
}

private class NestedThrowingResolver : ResourcePolicyResolver {
    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> =
        if (resource == "promotion") {
            throw IhawuCoreException("boom for $resource")
        } else {
            listOf(FieldPolicy("ssn", MaskingStrategy.REDACT, "***ssn"))
        }
}

class MaskingPropertyWriterTest {
    private val principal =
        IhawuPrincipal(
            "u1",
            roles = setOf("MANAGER"),
            attributes = emptyMap(),
        )

    @BeforeTest
    fun resetLogs() = LogRecorder.clear()

    /**
     * Serializes [employee] through an [IhawuModule] backed by [resolver] and returns the parsed
     * tree. The call carries [principal] unless [withPrincipal] is false, mirroring an
     * unauthenticated request.
     */
    private fun serialize(
        resolver: ResourcePolicyResolver,
        employee: Employee = Employee("Mary", "088856", emptyList()),
        withPrincipal: Boolean = true,
    ): JsonNode {
        val mapper = ObjectMapper().registerModule(IhawuModule(resolver))
        val writer = mapper.writer()
        val json =
            if (withPrincipal) {
                writer.withAttribute(IhawuSerialization.PRINCIPAL, principal).writeValueAsString(employee)
            } else {
                writer.writeValueAsString(employee)
            }
        return mapper.readTree(json)
    }

    @Test
    fun `serializes the resource as an empty object when no principal is provided`() {
        // No principal is a normal unauthenticated call, not an error: fail closed silently.
        val tree = serialize(CustomPolicyResolver(), withPrincipal = false)

        assertTrue(tree.isObject && tree.isEmpty)
    }

    @Test
    fun `warns with an actionable message and no payload when no principal is provided`() {
        serialize(CustomPolicyResolver(), withPrincipal = false)

        assertTrue(LogRecorder.lines.any { it.level == Level.WARN && it.message.contains("employee") })
        assertTrue(LogRecorder.lines.any { it.message.contains("serialization failing closed") })
        assertTrue(
            LogRecorder.lines.any {
                it.message.contains("Attach one via ObjectWriter.withAttribute(IhawuSerialization.PRINCIPAL")
            },
        )
        assertFalse(LogRecorder.lines.any { it.message.contains("088856") }) // payload never logged
    }

    @Test
    fun `serializes the resource as an empty object when the resolver throws`() {
        val tree = serialize(ThrowingResolver())

        assertTrue(tree.isObject && tree.isEmpty)
    }

    @Test
    fun `logs an error with no payload when the resolver throws`() {
        serialize(ThrowingResolver())

        assertTrue(LogRecorder.lines.any { it.level == Level.ERROR && it.message.contains("employee") })
        assertTrue(LogRecorder.lines.any { it.message.contains("serialization failing closed") })
        assertFalse(LogRecorder.lines.any { it.message.contains("088856") }) // payload never logged
    }

    @Test
    fun `redacts configured fields and leaves the rest unchanged`() {
        val tree =
            serialize(
                CustomPolicyResolver(),
                Employee("Mary", "088856", listOf(Promotion("2026-07-01", "AI Manager", 5000.0))),
            )

        assertEquals("Mary", tree["name"].asText())
        assertEquals("***ssn", tree["ssn"].asText())
        assertEquals(1, tree["promotions"].size())

        val actualPromo = tree["promotions"][0]

        assertEquals("2026-07-01", actualPromo["startDate"].asText())
        assertEquals("AI Manager", actualPromo["position"].asText())
        assertEquals("$***", actualPromo["salary"].asText())
    }

    @Test
    fun `omits a field entirely when its policy strategy is HIDE`() {
        val hidingResolver =
            object : ResourcePolicyResolver {
                override fun resolve(
                    principal: IhawuPrincipal,
                    resource: String,
                ): List<FieldPolicy> = listOf(FieldPolicy("ssn", MaskingStrategy.HIDE))
            }

        val tree = serialize(hidingResolver)

        assertFalse(tree.has("ssn")) // HIDE -> field omitted, not even a placeholder
        assertEquals("Mary", tree["name"].asText()) // unlisted field still passes through
    }

    @Test
    fun `redacts with the strategy default when no placeholder is configured`() {
        val resolver =
            object : ResourcePolicyResolver {
                override fun resolve(
                    principal: IhawuPrincipal,
                    resource: String,
                ): List<FieldPolicy> = listOf(FieldPolicy("ssn", MaskingStrategy.REDACT))
            }

        val tree = serialize(resolver)

        assertEquals("***-**-****", tree["ssn"].asText()) // falls back to MaskingStrategy.REDACT.defaultValue
    }

    @Test
    fun `fails a nested resource closed without affecting its parent`() {
        val tree =
            serialize(
                NestedThrowingResolver(),
                Employee(
                    "Mary",
                    "088856",
                    listOf(
                        Promotion("2026-07-01", "AI Manager", 5000.0),
                        Promotion("2026-08-01", "Director", 6000.0),
                    ),
                ),
            )

        assertEquals("Mary", tree["name"].asText()) // parent survives
        assertEquals("***ssn", tree["ssn"].asText()) // parent still masked
        assertTrue(tree["promotions"].all { it.isObject && it.isEmpty }) // every item {}
    }
}
