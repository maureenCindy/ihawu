# ADR 0002 — How policy resolution is cached for the request lifecycle

- **Status:** Accepted
- **Date:** 2026-06-25
- **Relates to:** #22 (request-scoped caching), #20 (serialization interceptor), #21 (config resolver)
- **Depends on:** #17 (`ResourcePolicyResolver`, `IhawuPrincipal`)
- **Builds on:** [ADR 0001](0001-serialization-context-passing.md)

## Context

A single HTTP response can serialize many `@IhawuResource` objects — a list endpoint returning
200 `EmployeeProfile`s, or a nested graph where each node is its own resource. For each one, the
masking serializer must resolve the caller's `List<FieldPolicy>` for that resource. Resolving
afresh every time is wasteful (200×, or 600× with nested types) and, worse, **unsafe for
correctness**: if the backing policy store changes mid-response, two objects in the *same* JSON
document could be masked under *different* policies.

The architecture calls for resolution to happen **once per request, reused for the request
lifecycle**. ADR 0001 already established a narrower, call-scoped version of this: the
`MaskingPropertyWriter` memoizes each resolved policy into the per-call `SerializerProvider`
attributes, so a single `writeValue` resolves each resource once. This ADR decides the broader,
**request-scoped** mechanism (#22) — one that spans multiple `writeValue` calls and is reusable
outside serialization entirely.

The core question:

> How does "resolve once per request" get achieved without (a) leaking cached policy across
> requests, (b) serving stale policy, or (c) coupling `ihawu-core` to any web framework?

This is security-adjacent: a masking library that caches the *wrong* answer — stale, or another
principal's — is a confidentiality bug. So, as in ADR 0001, the decision is judged by its
worst-case failure mode, not its happy path.

### The two caching layers (so they aren't confused)

| Layer | Scope | Lives in | Established by |
| --- | --- | --- | --- |
| Attribute memoization | one `writeValue` call | `MaskingPropertyWriter` via `SerializerProvider` attributes | ADR 0001 |
| **Caching decorator** | **one request (or any caller-chosen lifetime)** | **`CachingResourcePolicyResolver` in `ihawu-core`** | **this ADR** |

They are complementary: the attribute memo optimizes a single serialization pass; the decorator
caches across an entire request and in non-serialization contexts. #22 delivers only the
decorator; binding its lifetime to a real request is the Spring starter's job (a later issue).

## Options considered

### Option A — Caching decorator, scope = instance lifetime

A `CachingResourcePolicyResolver(delegate)` implements `ResourcePolicyResolver`, holds its own
`Map<(principal, resource), List<FieldPolicy>>`, and memoizes `resolve`. The cache lives exactly
as long as the *instance*. The caller (an adapter) controls scope by controlling the instance:
one per request → request-scoped; a shared singleton → application-scoped. The core never names
"request."

### Option B — Process-global cache with eviction (TTL / size, e.g. Caffeine)

A single long-lived cache in core, bounded by a time-to-live and/or maximum size.

### Option C — Inject a shared mutable cache map

The decorator takes a `MutableMap` the caller owns and scopes, with a `clear()` for reuse.

### Cross-cutting mechanism choices

- **At-most-once:** `ConcurrentHashMap.computeIfAbsent` (atomic) vs. `containsKey`-then-`put`.
- **Cache key:** the full `(IhawuPrincipal, resource)` vs. `(userId, resource)`.

## Analysis

### Security & correctness (decisive)

- **Option B is wrong for this domain.** A process-global cache, even with TTL, means a policy
  change is invisible until the entry expires — the library keeps masking by a stale policy for
  the TTL window. And a key collision or a too-coarse key risks serving one principal's policy to
  another. Eviction tuning becomes a security parameter, which is exactly the kind of "correct
  only if configured carefully" footgun ADR 0001 rejected.
- **Option A is correct by construction.** The cache cannot outlive the instance. Bind the
  instance to the request and the cache is born and dies with the request — it physically cannot
  go stale *within* a request (the only window where consistency matters) and cannot bleed into
  the next one. Freshness *between* requests is automatic, because each request gets a new cache.
- **Option C** is Option A with the lifetime turned into a mutable, caller-managed object. It is
  safe only if every code path remembers to scope or `clear()` it — a missed `clear()` silently
  serves a previous request's policies. It reintroduces the cleanup-discipline hazard that ADR
  0001 spent its length eliminating.

- **Key choice:** the key must be the full `(principal, resource)`. Keying by `userId` alone is a
  latent breach — two principals sharing an id but differing in `roles` resolve to *different*
  policies, so a `userId`-only key would serve one role-set's masking to another. `IhawuPrincipal`
  is a `data class`, so the full value is a correct, ready-made key.

### Performance

- The work saved is real: `computeIfAbsent` turns N resolves per response into one per distinct
  `(principal, resource)`. Within a single request the principal is constant, so in practice it
  collapses to one resolve per resource.
- **Atomic memoization matters.** `containsKey`-then-`put` can let two concurrent serializers both
  miss and both invoke the delegate — violating "at most once per key" and doubling the work on
  exactly the reactive/parallel paths where it is least affordable. `ConcurrentHashMap`'s
  `computeIfAbsent` runs the mapping function at most once per key, atomically, for free.
- Caching the *negative* result (`emptyList()` for an unknown resource/role) is deliberate: a list
  of unmasked items resolves "nothing applies here" once, not per item.

### Simplicity & framework-neutrality

- Option A needs no dependency (`java.util.concurrent` is the JDK, not a framework) and no config
  surface. The decorator is ~15 lines and composes with any `ResourcePolicyResolver`
  (`RoleBasedResourcePolicyResolver`, a future OPA/DB resolver, etc.).
- "Externally controllable scope" reduces to plain object lifetime — the most universally
  understood scoping primitive — so the same class works on MVC, WebFlux, coroutines, and batch
  jobs without knowing which it is on. The framework glue (a request-scoped bean) lands in the
  adapter, exactly where ADR 0001 also placed it.

## Decision

**Adopt Option A: a `CachingResourcePolicyResolver` decorator whose cache is an instance field,
keyed by `(principal, resource)`, populated via `ConcurrentHashMap.computeIfAbsent`.**

```kotlin
class CachingResourcePolicyResolver(
    private val delegate: ResourcePolicyResolver,
) : ResourcePolicyResolver {
    private val cache = ConcurrentHashMap<CacheKey, List<FieldPolicy>>()

    override fun resolve(principal: IhawuPrincipal, resource: String): List<FieldPolicy> =
        cache.computeIfAbsent(CacheKey(principal, resource)) { delegate.resolve(principal, resource) }

    private data class CacheKey(val principal: IhawuPrincipal, val resource: String)
}
```

Rationale, in priority order:

1. **Security/correctness** — request-bound lifetime makes stale-policy and cross-request bleed
   impossible by construction, not by eviction tuning or cleanup discipline.
2. **Performance** — `computeIfAbsent` gives atomic at-most-once resolution across all runtimes.
3. **Framework-neutrality** — scope is plain instance lifetime; the core never mentions "request."
4. **Simplicity** — a tiny decorator that composes with any resolver and adds zero dependencies.

## Consequences

### Data flow

```
ResourcePolicyResolver (e.g. RoleBasedResourcePolicyResolver, #21)
   │  wrapped by
   ▼
CachingResourcePolicyResolver            ← new instance per request (Spring starter, later issue)
   │  resolve(principal, resource)
   │    computeIfAbsent((principal, resource)) -> delegate.resolve(...)   ← at most once per key
   ▼
List<FieldPolicy>   (reused for every @IhawuResource in the response)
```

### Implications

- **#22** provides only the framework-neutral decorator. Lifetime binding (a request-scoped bean
  wrapping the application's resolver) is the **Spring starter's** responsibility.
- **Two layers coexist.** The decorator (request-scoped) and the ADR 0001 attribute memo
  (call-scoped) are complementary. When the decorator is wired as the serializer's resolver, it
  subsumes the per-call memo across the whole request; the call-scoped memo remains a correct,
  harmless inner optimization. A future cleanup *may* drop the serializer's own memoization once
  the decorator is always wired — explicitly out of scope here.
- **Scope is the caller's contract.** A shared/singleton instance caches application-wide (and can
  go stale) — that is a deliberate misuse the documentation warns against. The intended use is one
  instance per request.
- **Thread-safety** holds for concurrent serialization within a request via `ConcurrentHashMap`;
  the decorator adds no locks of its own.

## Alternatives rejected

- **Option B (process-global cache with eviction)** — rejected: serves stale policy for the TTL
  window and turns eviction tuning into a security parameter; freshness becomes "correct if
  configured carefully" rather than correct by construction.
- **Option C (injected/clearable shared map)** — rejected: correctness depends on the caller
  scoping or `clear()`-ing it; a missed reset silently serves a prior request's policies — the
  same cleanup-discipline hazard ADR 0001 eliminated.
- **`(userId, resource)` key** — rejected: ignores `roles`, which change the resolved policy; it
  would serve one role-set's masking to a different one.
- **`containsKey`-then-`put` memoization** — rejected: non-atomic, so concurrent callers can both
  invoke the delegate, breaking the "at most once per key" guarantee on reactive/parallel paths.
```