# ADR 0009 — The Ktor adapter: a custom ContentConverter + a coroutine-bridge per-call context

- **Status:** Accepted
- **Date:** 2026-07-18
- **Relates to:** #82 (ihawu-ktor adapter)
- **Depends on:** [ADR 0008](0008-kotlinx-serialization-masking.md) (kotlinx backend), [ADR 0006](0006-serialization-neutral-masking-spi.md) (neutral SPI)
- **Builds on:** [ADR 0008](0008-kotlinx-serialization-masking.md)

## Context

`ihawu-ktor` gives Ktor apps the zero-wiring experience the Spring Boot starter gives Spring. The engine
already exists (the kotlinx backend, #81), so the adapter is Ktor *plumbing* — but two Ktor-specific
mechanics had to be settled, and a spike (behind #82) proved them. This ADR records the design the spike
validated; both mechanics work with **no changes to core, the kotlinx backend, or the SPI**.

## Decision

### 1. Serializer selection — a custom `ContentConverter` (not `contextual`)
Ktor's `json()` resolves each type's default serializer, so `SerializersModule` `contextual` registration
would not intercept `@Serializable` classes. Instead, register a `MaskingContentConverter` with
`ContentNegotiation`: it resolves the response type's serializer
(`json.serializersModule.serializer(typeInfo.kotlinType!!)`), wraps it with the kotlinx backend's
`maskingSerializer(base, engine, registry)`, and encodes. **Every** response is wrapped — safe because the
backend's registry-driven recursion masks only registered `@IhawuResource` types and passes everything
else through untouched. Request bodies are not masked (`deserialize` decodes normally).

### 2. Per-call context — a send-pipeline intercept + the coroutine bridge
The masking transformer's `transformSerialize` is synchronous, so it reads the caller from a thread-local
(#81 / ADR 0008). The plugin resolves the principal per call and installs it around response serialization:

```kotlin
application.sendPipeline.intercept(ApplicationSendPipeline.Before) {
    withContext(maskingContextElement(resolve(call))) { proceed() }
}
```

`maskingContextElement` is `ThreadLocal.asContextElement(...)` from `ihawu-kotlinx`; wrapping `proceed()`
carries it onto the coroutine that runs `ContentNegotiation`'s encode. **The hook is the send pipeline,
not `ApplicationCallPipeline.Plugins`.** A `Plugins`-phase wrap resolves the principal *before* the
routing pipeline runs, so **route-level `authenticate { }` has not populated the call yet** and the bridge
would see `null` (a real failure the production tests caught; the spike used a header-based principal, which
is available immediately, and so missed it). `call.respond(...)` starts the send pipeline from inside the
authenticated handler, so resolving there sees the caller — and it still covers every response.

### 3. Identity bridge, caching, config, fail-closed
- **Identity bridge:** a configurable `suspend (ApplicationCall) -> IhawuPrincipal?` (default → `null`),
  the analogue of the starter's `PrincipalResolver`; production maps Ktor `Authentication` to `IhawuPrincipal`.
- **Per-call caching:** none is added — the neutral engine already resolves each resource once per call
  via `MaskingContext.memoize`, and the coroutine bridge installs a fresh `SimpleMaskingContext` (hence a
  fresh memo map) per call. That gives request-scoped caching without a `CachingResourcePolicyResolver`.
- **Config:** the plugin DSL (`install(IhawuKtor){ … }`) with the resolver, the principal bridge, and the
  resource registry. HOCON `ihawu.policies` binding is a follow-up and must be kotlinx/DSL-based, never
  `JacksonPolicyConfig` (no Jackson in a Ktor app).
- **Fail-closed:** no principal → `maskingContextElement(null)` → every field denied → `{}`. Matches the
  Jackson/starter contract.

### Ktor-3.x gotchas (captured so the impl and docs get them right)
- The `ContentConverter` method in Ktor 3.x is **`serialize(contentType, charset, typeInfo, value): OutgoingContent`** — *not* the 2.x `serializeNullable`. The wrong signature yields a misleading "overrides nothing / not abstract" error.
- **`Application.engine` shadows a local `engine`** inside `application { }` — name the masking engine `maskingEngine`, in code *and* doc snippets.
- The intercept block needs `import io.ktor.server.application.call`.

## Consequences

| Concern | Outcome |
| --- | --- |
| Zero-wiring for Ktor | `install(IhawuKtor){}` + `ContentNegotiation` registration (folded into one install for v1's ergonomics). |
| Serializer selection | Custom `ContentConverter` wrapping the masking serializer; every response wrapped, non-resources pass through. |
| Per-call context | Send-pipeline `withContext(maskingContextElement(principal))` — the #81 coroutine→thread-local bridge; resolved after route-level auth. |
| Fail-closed | No principal → `{}`, matching the other adapters. |
| Engine / core | Reused unchanged — no new engine, no core/kotlinx changes. |

### Out of scope (follow-ups)
- HOCON `ihawu.policies` config binding (kotlinx/DSL-based).
- Masking request bodies (adapter masks responses).
