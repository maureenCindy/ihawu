package com.ihawu.core.policy

import com.ihawu.core.exception.IhawuCoreException
import com.ihawu.core.masking.MaskingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoleBasedResourcePolicyResolverTest {
    @Test
    fun `returns empty list when no resource policies are configured`() {
        val resolver = RoleBasedResourcePolicyResolver(resourcePolicies = emptyList())
        val admin = IhawuPrincipal("user01", setOf("ADMIN"), emptyMap())
        val fieldPolicies = resolver.resolve(principal = admin, resource = "employee")
        assertTrue(fieldPolicies.isEmpty())
    }

    @Test
    fun `returns empty list when the principal has no roles`() {
        val rolePolicies =
            mapOf(
                "admin" to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "$---")),
            )
        val employeeResourcePolicy = ResourcePolicy("employee", rolePolicies)
        val resolver = RoleBasedResourcePolicyResolver(listOf(employeeResourcePolicy))
        val admin = IhawuPrincipal("user01", emptySet(), emptyMap())
        val fieldPolicies = resolver.resolve(principal = admin, resource = "employee")
        assertTrue(fieldPolicies.isEmpty())
    }

    @Test
    fun `returns empty list for an unknown resource`() {
        val rolePolicies =
            mapOf(
                "admin" to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "$---")),
            )
        val employeeResourcePolicy = ResourcePolicy("employee", rolePolicies)
        val resolver = RoleBasedResourcePolicyResolver(listOf(employeeResourcePolicy))
        val admin = IhawuPrincipal("user01", emptySet(), emptyMap())
        val fieldPolicies = resolver.resolve(principal = admin, resource = "user")
        assertTrue(fieldPolicies.isEmpty())
    }

    @Test
    fun `returns the field policies configured for a principal's role`() {
        val rolePolicies =
            mapOf(
                "admin" to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "$---")),
            )
        val employeeResourcePolicy = ResourcePolicy("employee", rolePolicies)
        val resolver = RoleBasedResourcePolicyResolver(listOf(employeeResourcePolicy))
        val admin = IhawuPrincipal("user01", setOf("admin"), emptyMap())
        val fieldPolicies = resolver.resolve(principal = admin, resource = "employee")

        assertEquals(1, fieldPolicies.size)
        assertTrue(
            fieldPolicies.containsAll(
                listOf(
                    FieldPolicy("salary", MaskingStrategy.REDACT, "$---"),
                ),
            ),
        )
    }

    @Test
    fun `keeps the most restrictive strategy when roles mask the same field`() {
        val rolePolicies =
            mapOf(
                "admin" to listOf(FieldPolicy("salary", MaskingStrategy.HIDE, "[hidden]")),
                "auditor" to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "$---")),
            )
        val employeeResourcePolicy = ResourcePolicy("employee", rolePolicies)
        val resolver = RoleBasedResourcePolicyResolver(listOf(employeeResourcePolicy))
        val admin = IhawuPrincipal("user01", setOf("admin", "auditor"), emptyMap())
        val fieldPolicies = resolver.resolve(principal = admin, resource = "employee")

        assertEquals(1, fieldPolicies.size)
        assertTrue(
            fieldPolicies.containsAll(
                listOf(
                    FieldPolicy("salary", MaskingStrategy.HIDE, "[hidden]"),
                ),
            ),
        )
    }

    @Test
    fun `breaks ties deterministically by role name when strategies are equal`() {
        val rolePolicies =
            mapOf(
                "admin" to listOf(FieldPolicy("salary", MaskingStrategy.HIDE, "[hidden]")),
                "auditor" to listOf(FieldPolicy("salary", MaskingStrategy.HIDE, "")),
            )
        val employeeResourcePolicy = ResourcePolicy("employee", rolePolicies)
        val resolver = RoleBasedResourcePolicyResolver(listOf(employeeResourcePolicy))
        val admin = IhawuPrincipal("user01", setOf("admin", "auditor"), emptyMap())
        val fieldPolicies = resolver.resolve(principal = admin, resource = "employee")

        assertEquals(1, fieldPolicies.size)
        assertTrue(
            fieldPolicies.containsAll(
                listOf(
                    FieldPolicy("salary", MaskingStrategy.HIDE, "[hidden]"),
                ),
            ),
        )
    }

    @Test
    fun `returns the union of field policies across a principal's roles`() {
        val rolePolicies =
            mapOf(
                "admin" to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "$---")),
                "auditor" to listOf(FieldPolicy("ssn", MaskingStrategy.HIDE, "[hidden]")),
            )
        val resolver =
            RoleBasedResourcePolicyResolver(
                listOf(
                    ResourcePolicy("employee", rolePolicies),
                ),
            )
        val principal =
            IhawuPrincipal(
                "user01",
                setOf("admin", "auditor"),
                emptyMap(),
            )

        val result = resolver.resolve(principal, "employee")

        assertEquals(2, result.size)
        assertTrue(
            result.containsAll(
                listOf(
                    FieldPolicy("salary", MaskingStrategy.REDACT, "$---"),
                    FieldPolicy("ssn", MaskingStrategy.HIDE, "[hidden]"),
                ),
            ),
        )
    }

    @Test
    fun `keeps the most restrictive strategy regardless of role order`() {
        val rolePolicies =
            mapOf(
                "admin" to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "$---")), // weaker, sorts FIRST
                "auditor" to listOf(FieldPolicy("salary", MaskingStrategy.HIDE, "[hidden]")), // stronger, sorts later
            )
        val resolver = RoleBasedResourcePolicyResolver(listOf(ResourcePolicy("employee", rolePolicies)))
        val principal = IhawuPrincipal("user01", setOf("admin", "auditor"), emptyMap())

        val result = resolver.resolve(principal, "employee")

        assertEquals(1, result.size)
        assertTrue(result.containsAll(listOf(FieldPolicy("salary", MaskingStrategy.HIDE, "[hidden]"))))
    }

    @Test
    fun `ignores a principal role that has no configured policy`() {
        val rolePolicies = mapOf("admin" to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "$---")))
        val resolver = RoleBasedResourcePolicyResolver(listOf(ResourcePolicy("employee", rolePolicies)))
        val principal = IhawuPrincipal("user01", setOf("manager"), emptyMap()) // role not in the map
        assertTrue(resolver.resolve(principal, "employee").isEmpty())
    }

    @Test
    fun `returns empty list when the resource has no role policies`() {
        val resolver = RoleBasedResourcePolicyResolver(listOf(ResourcePolicy("employee"))) // roleFieldPolicies defaults to null
        val principal = IhawuPrincipal("user01", setOf("admin"), emptyMap())
        assertTrue(resolver.resolve(principal, "employee").isEmpty())
    }

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

        val resolver = RoleBasedResourcePolicyResolver.fromJson(config)
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
            RoleBasedResourcePolicyResolver.fromJson("{ not json")
        }
    }

    @Test
    fun `fromJson throws on an unknown masking strategy`() {
        val config =
            """
            { "employee": { "admin": [ { "field": "salary", "strategy": "SCRAMBLE" } ] } }
            """.trimIndent()

        assertFailsWith<IhawuCoreException> {
            RoleBasedResourcePolicyResolver.fromJson(config)
        }
    }

    @Test
    fun `fromJson reads configuration from an input stream`() {
        val config =
            """
            { "employee": { "admin": [ { "field": "ssn", "strategy": "HIDE" } ] } }
            """.trimIndent()

        val resolver = RoleBasedResourcePolicyResolver.fromJson(config.byteInputStream())
        val principal = IhawuPrincipal("user01", setOf("admin"), emptyMap())

        assertEquals(listOf(FieldPolicy("ssn", MaskingStrategy.HIDE, null)), resolver.resolve(principal, "employee"))
    }

    @Test
    fun `fromJson throws when the configuration is not a JSON object`() {
        assertFailsWith<IhawuCoreException> {
            RoleBasedResourcePolicyResolver.fromJson("[]")
        }
    }

    @Test
    fun `fromJson throws when a field policy is missing its field`() {
        val config =
            """
            { "employee": { "admin": [ { "strategy": "HIDE" } ] } }
            """.trimIndent()

        assertFailsWith<IhawuCoreException> {
            RoleBasedResourcePolicyResolver.fromJson(config)
        }
    }

    @Test
    fun `fromJson throws when a resource does not map roles to policies`() {
        assertFailsWith<IhawuCoreException> {
            RoleBasedResourcePolicyResolver.fromJson("""{ "employee": "nope" }""")
        }
    }

    @Test
    fun `fromJson throws when a role is not an array of policies`() {
        assertFailsWith<IhawuCoreException> {
            RoleBasedResourcePolicyResolver.fromJson("""{ "employee": { "admin": "nope" } }""")
        }
    }

    @Test
    fun `fromJson throws when a field policy's field is not textual`() {
        val config =
            """
            { "employee": { "admin": [ { "field": 123, "strategy": "HIDE" } ] } }
            """.trimIndent()

        val exception = assertFailsWith<IhawuCoreException> { RoleBasedResourcePolicyResolver.fromJson(config) }
        assertTrue(exception.message.contains("textual 'field'"))
    }

    @Test
    fun `fromJson throws when a field policy is missing its strategy`() {
        val config =
            """
            { "employee": { "admin": [ { "field": "salary" } ] } }
            """.trimIndent()

        val exception = assertFailsWith<IhawuCoreException> { RoleBasedResourcePolicyResolver.fromJson(config) }
        assertTrue(exception.message.contains("missing a textual 'strategy'"))
    }

    @Test
    fun `fromJson throws when a field policy's strategy is not textual`() {
        val config =
            """
            { "employee": { "admin": [ { "field": "salary", "strategy": 5 } ] } }
            """.trimIndent()

        val exception = assertFailsWith<IhawuCoreException> { RoleBasedResourcePolicyResolver.fromJson(config) }
        assertTrue(exception.message.contains("missing a textual 'strategy'"))
    }

    @Test
    fun `fromJson ignores a non-textual placeholder`() {
        val config =
            """
            { "employee": { "admin": [ { "field": "salary", "strategy": "REDACT", "placeholder": 123 } ] } }
            """.trimIndent()

        val resolver = RoleBasedResourcePolicyResolver.fromJson(config)
        val principal = IhawuPrincipal("user01", setOf("admin"), emptyMap())

        // A non-textual placeholder is ignored (treated as absent), not an error.
        assertEquals(
            listOf(FieldPolicy("salary", MaskingStrategy.REDACT, null)),
            resolver.resolve(principal, "employee"),
        )
    }
}
