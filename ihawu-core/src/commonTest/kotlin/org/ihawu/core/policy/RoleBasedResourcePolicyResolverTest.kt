package org.ihawu.core.policy

import org.ihawu.core.masking.MaskingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
