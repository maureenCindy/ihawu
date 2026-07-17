package org.ihawu.core.serialization

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@IhawuResource("worker")
private data class Worker(
    val name: String, // non-null String
    val nickname: String?, // nullable String
    val salary: Int, // non-null non-String -> UNSAFE
    val bonus: Int?, // nullable non-String
    @JsonProperty("acct_balance") val balance: Int?, // renamed nullable
)

class MaskingContractValidatorTest {
    private val mapper = ObjectMapper()

    private fun validate(vararg policies: FieldPolicy): List<MaskingContractViolation> =
        MaskingContractValidator.validate(mapper, "worker", Worker::class.java, policies.toList())

    @Test
    fun `accepts REDACT on String, nullable String, and nullable non-String`() {
        val violations =
            validate(
                FieldPolicy("name", MaskingStrategy.REDACT, "***"),
                FieldPolicy("nickname", MaskingStrategy.REDACT, "***"),
                FieldPolicy("bonus", MaskingStrategy.REDACT),
            )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `rejects REDACT on a non-nullable non-String field`() {
        val violations = validate(FieldPolicy("salary", MaskingStrategy.REDACT, "***"))

        assertEquals(1, violations.size)
        assertEquals("worker", violations[0].resource)
        assertEquals("salary", violations[0].field)
        assertTrue(violations[0].reason.contains("REDACT"))
    }

    @Test
    fun `accepts HIDE on nullable fields but rejects it on non-nullable ones`() {
        val violations =
            validate(
                FieldPolicy("nickname", MaskingStrategy.HIDE), // nullable String -> ok
                FieldPolicy("bonus", MaskingStrategy.HIDE), // nullable Int -> ok
                FieldPolicy("name", MaskingStrategy.HIDE), // non-null String -> reject
                FieldPolicy("salary", MaskingStrategy.HIDE), // non-null Int -> reject
            )

        assertEquals(setOf("name", "salary"), violations.map { it.field }.toSet())
        assertTrue(violations.all { it.reason.contains("HIDE") })
    }

    @Test
    fun `resolves nullability through a JsonProperty rename`() {
        // The policy targets the serialized name; nullability must resolve via the logical name, so
        // this renamed nullable Int is accepted rather than flagged UNSAFE.
        val violations = validate(FieldPolicy("acct_balance", MaskingStrategy.REDACT, "***"))

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `flags a policy that targets an unknown field`() {
        // A typo'd field masks nothing, silently leaving a sensitive field exposed.
        val violations = validate(FieldPolicy("does_not_exist", MaskingStrategy.REDACT, "***"))

        assertEquals(1, violations.size)
        assertEquals("does_not_exist", violations[0].field)
        assertTrue(violations[0].reason.contains("not a serialized property"))
    }
}
