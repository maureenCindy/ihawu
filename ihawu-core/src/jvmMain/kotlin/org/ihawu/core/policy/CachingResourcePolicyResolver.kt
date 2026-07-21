package org.ihawu.core.policy

import java.util.concurrent.ConcurrentHashMap

/**
 * A [ResourcePolicyResolver] decorator that memoizes the wrapped resolver's results, so resolution
 * happens at most once per `(principal, resource)` for the lifetime of this instance.
 *
 * A single response can serialize many `@IhawuResource` objects (a list endpoint, a nested graph);
 * without caching, the underlying resolver would be invoked once per object — wasteful, and unsafe
 * if the backing store changes mid-response, which could mask two objects in the same document under
 * different policies. This decorator resolves each key once and reuses the result, including the
 * empty "no policy applies" result.
 *
 * **Scope is the instance lifetime.** The cache is held in this object and dies with it; the core
 * never refers to "request." Callers control scope by controlling the instance — create one per
 * request (e.g. a request-scoped bean in the Spring starter) for request-scoped caching with
 * automatic freshness between requests. A single shared instance caches application-wide and can
 * serve stale policy; that is a deliberate misuse, not the intended pattern.
 *
 * Memoization uses [ConcurrentHashMap.computeIfAbsent], so the wrapped resolver is invoked at most
 * once per key even under concurrent serialization. The result is returned by reference and not
 * copied; callers must honour the read-only [List] contract and not mutate it.
 *
 * @property delegate The resolver whose results are memoized (e.g. [RoleBasedResourcePolicyResolver]).
 * @sample org.ihawu.samples.policy.cacheResolvesOncePerScope
 * @see ResourcePolicyResolver
 */
public class CachingResourcePolicyResolver(
    private val delegate: ResourcePolicyResolver,
) : ResourcePolicyResolver {
    private val cache = ConcurrentHashMap<CacheKey, List<FieldPolicy>>()

    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> =
        cache.computeIfAbsent(CacheKey(principal, resource)) {
            delegate.resolve(principal, resource)
        }

    private data class CacheKey(
        val principal: IhawuPrincipal,
        val resource: String,
    )
}
