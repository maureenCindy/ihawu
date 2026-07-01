package org.ihawu.samples.policy

import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.CachingResourcePolicyResolver
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver

fun cacheResolvesOncePerScope() {
    // A resolver that records how often it is consulted.
    var calls = 0
    val counting =
        object : ResourcePolicyResolver {
            override fun resolve(
                principal: IhawuPrincipal,
                resource: String,
            ): List<FieldPolicy> {
                calls++
                return listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "***"))
            }
        }

    // Wrap it: within one instance, repeated resolves of the same key hit the delegate once.
    val resolver = CachingResourcePolicyResolver(counting)
    val principal = IhawuPrincipal("u1", roles = setOf("MANAGER"), attributes = emptyMap())

    resolver.resolve(principal, "employee")
    resolver.resolve(principal, "employee")

    check(calls == 1) // resolved once; the second call is served from the cache
}
