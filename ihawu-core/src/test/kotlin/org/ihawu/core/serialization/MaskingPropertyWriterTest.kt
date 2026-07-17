package org.ihawu.core.serialization

import com.fasterxml.jackson.annotation.JsonProperty
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
    val salary: Double?, // nullable non-String: REDACT masks to JSON null
)

@IhawuResource("employee")
private data class Employee(
    val name: String,
    val ssn: String,
    val promotions: List<Promotion>,
    val nickname: String? = null, // nullable String: REDACT -> placeholder, HIDE -> silent omit
)

/** A non-nullable non-String field is UNSAFE to redact — there is no contract-safe masked value. */
@IhawuResource("payslip")
private data class Payslip(
    val employeeId: String,
    val netPay: Int,
)

/** The masked field is renamed, so nullability must resolve via the logical property name. */
@IhawuResource("account_balance")
private data class AccountBalance(
    val id: String,
    @JsonProperty("acct_balance") val balance: Int?,
)

/** No primary constructor (secondary only): nullability is unknown, so fields default to non-nullable. */
@IhawuResource("legacy")
private class Legacy {
    val reference: String
    val amount: Int

    constructor(reference: String, amount: Int) {
        this.reference = reference
        this.amount = amount
    }
}

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

    /** Serializes any [value] through an [IhawuModule] backed by [resolver], carrying [principal]. */
    private fun serializeValue(
        resolver: ResourcePolicyResolver,
        value: Any,
    ): JsonNode {
        val mapper = ObjectMapper().registerModule(IhawuModule(resolver))
        val json =
            mapper
                .writer()
                .withAttribute(IhawuSerialization.PRINCIPAL, principal)
                .writeValueAsString(value)
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
        // nullable non-String REDACT -> JSON null, not a plausible fake number (the configured
        // "$***" placeholder does not apply to non-String fields).
        assertTrue(actualPromo.has("salary") && actualPromo["salary"].isNull)
    }

    @Test
    fun `omits a nullable field silently when its policy strategy is HIDE`() {
        val hidingResolver =
            object : ResourcePolicyResolver {
                override fun resolve(
                    principal: IhawuPrincipal,
                    resource: String,
                ): List<FieldPolicy> = listOf(FieldPolicy("acct_balance", MaskingStrategy.HIDE))
            }

        val tree = serializeValue(hidingResolver, AccountBalance("acc_1", 4200))

        assertFalse(tree.has("acct_balance")) // nullable/optional -> HIDE omits, contract-safe
        assertEquals("acc_1", tree["id"].asText()) // unlisted field still passes through
        assertFalse(LogRecorder.lines.any { it.level == Level.ERROR }) // valid HIDE -> no error
    }

    @Test
    fun `fails closed with a log when HIDE targets a non-nullable field`() {
        // HIDE on a required field still omits (never leak), but flags the misconfiguration; the loud
        // hard-fail lives in startup validation, not the hot path.
        val hidingResolver =
            object : ResourcePolicyResolver {
                override fun resolve(
                    principal: IhawuPrincipal,
                    resource: String,
                ): List<FieldPolicy> = listOf(FieldPolicy("ssn", MaskingStrategy.HIDE))
            }

        val tree = serialize(hidingResolver)

        assertFalse(tree.has("ssn")) // still omitted -> no leak
        assertEquals("Mary", tree["name"].asText()) // unlisted field still passes through
        assertTrue(
            LogRecorder.lines.any {
                it.level == Level.ERROR && it.message.contains("employee") && it.message.contains("ssn")
            },
        )
        assertFalse(LogRecorder.lines.any { it.message.contains("088856") }) // payload never logged
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

        assertEquals("***-**-****", tree["ssn"].asText()) // falls back to MaskingStrategy.REDACT.defaultPlaceholder
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

    @Test
    fun `redacts a nullable String field with its placeholder`() {
        // A nullable String redacts exactly like a non-null String — the placeholder, not JSON null.
        val resolver =
            object : ResourcePolicyResolver {
                override fun resolve(
                    principal: IhawuPrincipal,
                    resource: String,
                ): List<FieldPolicy> = listOf(FieldPolicy("nickname", MaskingStrategy.REDACT, "***nick"))
            }

        val tree = serialize(resolver, Employee("Mary", "088856", emptyList(), nickname = "Mimi"))

        assertEquals("***nick", tree["nickname"].asText())
    }

    @Test
    fun `omits a nullable String field silently under HIDE`() {
        val resolver =
            object : ResourcePolicyResolver {
                override fun resolve(
                    principal: IhawuPrincipal,
                    resource: String,
                ): List<FieldPolicy> = listOf(FieldPolicy("nickname", MaskingStrategy.HIDE))
            }

        val tree = serialize(resolver, Employee("Mary", "088856", emptyList(), nickname = "Mimi"))

        assertFalse(tree.has("nickname")) // nullable -> HIDE omits, contract-safe
        assertEquals("Mary", tree["name"].asText())
        assertFalse(LogRecorder.lines.any { it.level == Level.ERROR }) // valid HIDE -> no error
    }

    @Test
    fun `fails closed when REDACT targets a non-nullable non-String field`() {
        val resolver =
            object : ResourcePolicyResolver {
                override fun resolve(
                    principal: IhawuPrincipal,
                    resource: String,
                ): List<FieldPolicy> = listOf(FieldPolicy("netPay", MaskingStrategy.REDACT, "***"))
            }

        val tree = serializeValue(resolver, Payslip("emp_1", 5000))

        assertFalse(tree.has("netPay")) // UNSAFE -> omitted, never a plausible fake number
        assertEquals("emp_1", tree["employeeId"].asText()) // unmasked field passes through
        assertTrue(
            LogRecorder.lines.any {
                it.level == Level.ERROR && it.message.contains("payslip") && it.message.contains("netPay")
            },
        )
        assertFalse(LogRecorder.lines.any { it.message.contains("5000") }) // value never logged
    }

    @Test
    fun `treats fields as non-nullable when the type has no primary constructor`() {
        val resolver =
            object : ResourcePolicyResolver {
                override fun resolve(
                    principal: IhawuPrincipal,
                    resource: String,
                ): List<FieldPolicy> = listOf(FieldPolicy("amount", MaskingStrategy.REDACT, "***"))
            }

        val tree = serializeValue(resolver, Legacy("ref-9", 500))

        assertFalse(tree.has("amount")) // no primary ctor -> assumed non-nullable -> UNSAFE -> omitted
        assertEquals("ref-9", tree["reference"].asText())
    }

    @Test
    fun `resolves nullability through a JsonProperty rename`() {
        // The policy targets the serialized name; nullability must resolve via the logical property
        // name. Without the serialized -> logical bridge, balance reads as non-nullable -> UNSAFE ->
        // omitted, so this asserting a present JSON null guards that regression.
        val resolver =
            object : ResourcePolicyResolver {
                override fun resolve(
                    principal: IhawuPrincipal,
                    resource: String,
                ): List<FieldPolicy> = listOf(FieldPolicy("acct_balance", MaskingStrategy.REDACT, "***"))
            }

        val tree = serializeValue(resolver, AccountBalance("acc_1", 4200))

        assertTrue(tree.has("acct_balance") && tree["acct_balance"].isNull) // nullable Int? -> JSON null
    }
}
