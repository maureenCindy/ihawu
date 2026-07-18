package org.ihawu.jackson

import org.ihawu.core.exception.IhawuCoreException
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JacksonPolicyConfigTest {
    @Test
    fun `fromJson parses resource role and field policies`() {
        val config =
            """
            {
              "employee": {
                "admin": [
                  { "field": "salary", "strategy": "REDACT", "placeholder": "***" },
                  { "field": "ssn", "strategy": "HIDE" }
                ]
              }
            }
            """.trimIndent()

        val resolver = JacksonPolicyConfig.fromJson(config)
        val principal = IhawuPrincipal("user01", setOf("admin"), emptyMap())
        val policies = resolver.resolve(principal, "employee")

        assertEquals(2, policies.size)
        assertTrue(
            policies.containsAll(
                listOf(
                    FieldPolicy("salary", MaskingStrategy.REDACT, "***"),
                    FieldPolicy("ssn", MaskingStrategy.HIDE, null),
                ),
            ),
        )
    }

    @Test
    fun `fromJson throws on invalid JSON`() {
        assertFailsWith<IhawuCoreException> {
            JacksonPolicyConfig.fromJson("{ not json")
        }
    }

    @Test
    fun `fromJson throws on an unknown masking strategy`() {
        val config =
            """
            { "employee": { "admin": [ { "field": "salary", "strategy": "SCRAMBLE" } ] } }
            """.trimIndent()

        assertFailsWith<IhawuCoreException> {
            JacksonPolicyConfig.fromJson(config)
        }
    }

    @Test
    fun `fromJson reads configuration from an input stream`() {
        val config =
            """
            { "employee": { "admin": [ { "field": "ssn", "strategy": "HIDE" } ] } }
            """.trimIndent()

        val resolver = JacksonPolicyConfig.fromJson(config.byteInputStream())
        val principal = IhawuPrincipal("user01", setOf("admin"), emptyMap())

        assertEquals(listOf(FieldPolicy("ssn", MaskingStrategy.HIDE, null)), resolver.resolve(principal, "employee"))
    }

    @Test
    fun `fromJson throws when the configuration is not a JSON object`() {
        assertFailsWith<IhawuCoreException> {
            JacksonPolicyConfig.fromJson("[]")
        }
    }

    @Test
    fun `fromJson throws when a field policy is missing its field`() {
        val config =
            """
            { "employee": { "admin": [ { "strategy": "HIDE" } ] } }
            """.trimIndent()

        assertFailsWith<IhawuCoreException> {
            JacksonPolicyConfig.fromJson(config)
        }
    }

    @Test
    fun `fromJson throws when a resource does not map roles to policies`() {
        assertFailsWith<IhawuCoreException> {
            JacksonPolicyConfig.fromJson("""{ "employee": "nope" }""")
        }
    }

    @Test
    fun `fromJson throws when a role is not an array of policies`() {
        assertFailsWith<IhawuCoreException> {
            JacksonPolicyConfig.fromJson("""{ "employee": { "admin": "nope" } }""")
        }
    }

    @Test
    fun `fromJson throws when a field policy's field is not textual`() {
        val config =
            """
            { "employee": { "admin": [ { "field": 123, "strategy": "HIDE" } ] } }
            """.trimIndent()

        val exception = assertFailsWith<IhawuCoreException> { JacksonPolicyConfig.fromJson(config) }
        assertTrue(exception.message.contains("textual 'field'"))
    }

    @Test
    fun `fromJson throws when a field policy is missing its strategy`() {
        val config =
            """
            { "employee": { "admin": [ { "field": "salary" } ] } }
            """.trimIndent()

        val exception = assertFailsWith<IhawuCoreException> { JacksonPolicyConfig.fromJson(config) }
        assertTrue(exception.message.contains("missing a textual 'strategy'"))
    }

    @Test
    fun `fromJson throws when a field policy's strategy is not textual`() {
        val config =
            """
            { "employee": { "admin": [ { "field": "salary", "strategy": 5 } ] } }
            """.trimIndent()

        val exception = assertFailsWith<IhawuCoreException> { JacksonPolicyConfig.fromJson(config) }
        assertTrue(exception.message.contains("missing a textual 'strategy'"))
    }

    @Test
    fun `fromJson ignores a non-textual placeholder`() {
        val config =
            """
            { "employee": { "admin": [ { "field": "salary", "strategy": "REDACT", "placeholder": 123 } ] } }
            """.trimIndent()

        val resolver = JacksonPolicyConfig.fromJson(config)
        val principal = IhawuPrincipal("user01", setOf("admin"), emptyMap())

        // A non-textual placeholder is ignored (treated as absent), not an error.
        assertEquals(
            listOf(FieldPolicy("salary", MaskingStrategy.REDACT, null)),
            resolver.resolve(principal, "employee"),
        )
    }
}
