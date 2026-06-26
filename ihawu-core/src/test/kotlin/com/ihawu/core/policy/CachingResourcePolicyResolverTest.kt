package com.ihawu.core.policy

import com.ihawu.core.masking.MaskingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CachingResourcePolicyResolverTest {
    @Test
    fun `resolves a repeated key only once and reuses the result`() {
        val salaryPolicy = listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "***"))
        val delegate = CountingResolver { _, _ -> salaryPolicy }
        val resolver = CachingResourcePolicyResolver(delegate)
        val principal = IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())

        val first = resolver.resolve(principal, "employee")
        val second = resolver.resolve(principal, "employee")

        assertEquals(1, delegate.calls.size) // delegate hit once despite two resolves
        assertEquals(salaryPolicy, first) // correctness: returns the delegate's answer
        assertEquals(first, second) // second call served from cache, identical
    }

    @Test
    fun `caches each resource independently`() {
        val byResource =
            mapOf(
                "employee" to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "***")),
                "patient" to listOf(FieldPolicy("ssn", MaskingStrategy.HIDE)),
            )
        val delegate = CountingResolver { _, resource -> byResource.getValue(resource) }
        val resolver = CachingResourcePolicyResolver(delegate)
        val principal = IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())

        val employee = resolver.resolve(principal, "employee")
        val patient = resolver.resolve(principal, "patient")

        assertEquals(2, delegate.calls.size) // distinct resources -> two delegate calls
        assertEquals(byResource["employee"], employee)
        assertEquals(byResource["patient"], patient)
    }

    @Test
    fun `caches each principal independently including their roles`() {
        // Same userId and resource, differing only in roles: the cache must NOT collapse them,
        // or one role-set's masking would be served to another principal (a confidentiality breach).
        val manager = IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())
        val auditor = IhawuPrincipal("u1", setOf("AUDITOR"), emptyMap())
        val byPrincipal =
            mapOf(
                manager to listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "***")),
                auditor to listOf(FieldPolicy("salary", MaskingStrategy.HIDE)),
            )
        val delegate = CountingResolver { principal, _ -> byPrincipal.getValue(principal) }
        val resolver = CachingResourcePolicyResolver(delegate)

        val managerPolicies = resolver.resolve(manager, "employee")
        val auditorPolicies = resolver.resolve(auditor, "employee")

        assertEquals(2, delegate.calls.size) // roles differ -> distinct keys -> two delegate calls
        assertEquals(byPrincipal[manager], managerPolicies)
        assertEquals(byPrincipal[auditor], auditorPolicies)
    }

    @Test
    fun `caches an empty result so unknowns are resolved only once`() {
        val delegate = CountingResolver() // default stub resolves nothing (fail-open empty list)
        val resolver = CachingResourcePolicyResolver(delegate)
        val principal = IhawuPrincipal("u1", setOf("GUEST"), emptyMap())

        val first = resolver.resolve(principal, "employee")
        val second = resolver.resolve(principal, "employee")

        assertEquals(1, delegate.calls.size) // the empty answer is cached, not retried per call
        assertTrue(first.isEmpty())
        assertEquals(first, second)
    }

    @Test
    fun `each decorator instance has its own cache scope`() {
        val delegate = CountingResolver { _, _ -> listOf(FieldPolicy("salary", MaskingStrategy.HIDE)) }
        val principal = IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())

        // Two independently-scoped decorators over the SAME delegate (e.g. two requests).
        CachingResourcePolicyResolver(delegate).resolve(principal, "employee")
        CachingResourcePolicyResolver(delegate).resolve(principal, "employee")

        assertEquals(2, delegate.calls.size) // a fresh instance = a fresh cache -> delegate resolves again
    }

    @Test
    fun `returns the delegate's exact result, cached by reference`() {
        val policies = listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "***"))
        val delegate = CountingResolver { _, _ -> policies }
        val resolver = CachingResourcePolicyResolver(delegate)
        val principal = IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())

        val first = resolver.resolve(principal, "employee")
        val second = resolver.resolve(principal, "employee")

        assertSame(policies, first) // delegate's exact list, not a copy or wrapper
        assertSame(first, second) // the cached read serves the same reference
    }
}

private class CountingResolver(
    private val stub: (IhawuPrincipal, String) -> List<FieldPolicy> = { _, _ -> emptyList() },
) : ResourcePolicyResolver {
    val calls = mutableListOf<Pair<IhawuPrincipal, String>>()

    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> {
        calls += principal to resource
        return stub(principal, resource)
    }
}
