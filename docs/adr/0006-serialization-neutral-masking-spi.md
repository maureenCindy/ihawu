# ADR 0006 — A serialization-neutral masking SPI

- **Status:** Accepted
- **Date:** 2026-07-18
- **Relates to:** #77 (extract a serialization-neutral masking SPI) — keystone of 0.3.0
- **Depends on:** #67 / #68 (the type contract, [ADR 0005](0005-hard-fail-on-unenforceable-masking-policy.md))
- **Builds on:** [ADR 0001](0001-serialization-context-passing.md) (how the principal reaches the serializer)

## Context

Until now the enforcement point *was* Jackson. `IhawuBeanSerializerModifier` implemented Jackson's
`BeanSerializerModifier` and wrapped each `BeanPropertyWriter` in a `MaskingPropertyWriter` that did
everything: resolve policy, memoize it on the `SerializerProvider`, fail closed, apply the type
contract, and write the result. "Decide what to do with a field" and "write it through Jackson" were the
same class.

That coupling is what blocks Kotlin Multiplatform (Jackson is JVM-only) and what confines masking to the
HTTP JSON path. Most of the engine's actual value — policy resolution, per-call memoization,
fail-closed behaviour, the type contract — has nothing to do with Jackson.

## Decision

Split the engine along the *decide* / *write* seam.

**A neutral decision contract in `ihawu-core` (`org.ihawu.core.masking`), no `com.fasterxml.jackson.*`
in any signature:**

- `MaskingEngine.decide(resource, field, capability, context): MaskingDecision` — the whole contract.
- `MaskingDecision` — `Pass | Omit(reason?) | WriteString(value) | WriteNull`.
- `MaskingCapability` — the type classification from ADR 0005, now neutral: a backend supplies the two
  facts it needs (`isTextual`, `nullable`); Jackson's `JavaType` no longer appears in it.
- `MaskingContext` — `principal` + `memoize(key, compute)`, the call-scoped context.
- `MaskingFailureSink` — notified on each fail-closed drop.
- `DefaultMaskingEngine` — resolves policy once per call via `memoize`, fails closed, holds no
  serialization types.

The Jackson code becomes a thin *backend*: `IhawuBeanSerializerModifier` derives each field's
`MaskingCapability` from `JavaType` once per type; `MaskingPropertyWriter` adapts `SerializerProvider`
to `MaskingContext`, calls `decide`, and executes the `MaskingDecision`.

### Four choices worth recording

1. **A per-field decision function backends *call*, not a per-writer hook.** Jackson lets us wrap each
   `BeanPropertyWriter`; `kotlinx.serialization` (#81) has **no** equivalent — serializers are generated
   at compile time, there is no writer to wrap. So the SPI is "given a field, return a decision"; *when
   and how* a backend invokes it, and how it recurses into nested resources and collections, are the
   backend's concern. A per-writer-shaped SPI would have been un-implementable on kotlinx.

2. **The engine renders the decision fully** (`WriteString`/`WriteNull`), rather than returning a
   high-level "redact" for each backend to interpret. This keeps the declared-type contract in **one**
   place (ADR 0005's invariant), so a second backend cannot drift from the first — the same reason
   `MaskingCapabilities` is the single source of capabilities today.

3. **`MaskingContext` replaces reliance on `SerializerProvider` attributes.** Per-call memoization and
   principal passing were expressed against Jackson; they are now expressed against a neutral,
   call-scoped abstraction the Jackson backend adapts. Scoping to one write call (not a thread-bound
   holder) keeps cross-request contamination structurally impossible, as in ADR 0001.

4. **A minimal `MaskingFailureSink` keeps the engine logging-neutral.** Moving policy resolution and
   fail-closed behaviour into the neutral engine (as this issue requires) would otherwise drag SLF4J —
   which is JVM-only (#79) — into `commonMain`. Instead the engine notifies a sink; the Jackson backend
   supplies an SLF4J-backed implementation (`Slf4jMaskingFailureSink`) that logs the exact same lines as
   before. Resource-level failures fire once per (call, resource) because `memoize` runs resolution
   once. This sink is deliberately the smallest thing that preserves today's observability; the richer
   failure-listener with metrics and a configurable fail-request mode (#89) is expected to build on it.

## Scope

This ADR is the *seam*. It does **not** move the Jackson backend out of `ihawu-core` (that is #78, and
is where `ihawu-core` finally drops the `jackson-databind` dependency), remove SLF4J (#79), or add any
target (#80). Runtime behaviour is unchanged and the full pre-existing test suite passes.

## Consequences

| Concern | Outcome |
| --- | --- |
| Kotlin Multiplatform | The masking model can move to `commonMain` once #78/#79 remove the last JVM deps; nothing in `org.ihawu.core.masking` references Jackson or SLF4J. |
| A second backend (kotlinx, #81) | Implements `MaskingContext` + capability derivation and calls the same `MaskingEngine`; the decision logic and type contract are reused, not reimplemented. |
| Public API | New neutral SPI types are public. `IhawuModule(resolver)`, `MaskingContractValidator`, and `MaskingContractViolation` keep their signatures and locations (they relocate in #78). |
| Hot path | Capability is still derived once per type; per field the writer allocates a thin `MaskingContext` adapter and calls `decide` — no per-call reflection. |
| Observability | Identical: the SLF4J sink logs the same messages/levels; the startup validator reuses the engine's own `unenforceableReason` predicate, so startup and runtime verdicts cannot diverge. |
