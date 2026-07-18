# ADR 0008 — Masking on kotlinx.serialization: JsonTransformingSerializer + registry, thread-local context

- **Status:** Accepted
- **Date:** 2026-07-18
- **Relates to:** #81 (kotlinx.serialization masking)
- **Depends on:** [ADR 0006](0006-serialization-neutral-masking-spi.md) (neutral SPI), #80 (KMP core)
- **Builds on:** [ADR 0006](0006-serialization-neutral-masking-spi.md)

## Context

The Jackson backend intercepts a runtime serializer graph (`BeanSerializerModifier` → wrap each
`BeanPropertyWriter`), which is why nested types and collections mask for free.
`kotlinx.serialization` is fundamentally different: serializers are generated at **compile time**, so
there is **no per-property writer to wrap**, and **no per-call context channel** (no equivalent of
Jackson's `SerializerProvider` attributes). Masking on kotlinx is therefore a *second engine*, not a port.

A spike (behind #81) built a working multiplatform prototype and answered the open questions. This ADR
records the design it validated.

## Decision

Implement masking on kotlinx via a **`JsonTransformingSerializer`** that reuses `ihawu-core`'s neutral SPI
unchanged: it lets a value serialize to a `JsonElement`, then rewrites the `JsonObject` per
`MaskingEngine.decide(...)` — `Pass` keeps the entry, `Omit` drops the key, `WriteString`/`WriteNull`
replace it. Capability comes straight from the `SerialDescriptor`
(`getElementDescriptor(i).kind == PrimitiveKind.STRING` → textual; `.isNullable` → nullable). The spike
proved this correct on **both JVM and JS**, reusing `DefaultMaskingEngine`/`MaskingCapability`/
`MaskingDecision` with **zero core changes** — the first proof the #77 seam holds across engines.

Four sub-decisions:

1. **Resource identity via explicit registration**, not annotations. `SerialDescriptor.annotations` only
   exposes `@SerialInfo` annotations, and `@IhawuResource` is deliberately neutral (it must not drag
   `kotlinx-serialization-core` onto every consumer, including Jackson-only ones). So kotlinx resources are
   known through an explicit registry (`serialName → resourceName`); `@IhawuResource` stays kotlinx-free and
   is optional for this backend. **The descriptor's `serialName` for a nullable field carries a trailing
   `?` — the registry lookup must normalize it** (a real bug the spike hit).

2. **Recursion is manual, driven by the registry** — kotlinx gives no free recursion. The transformer
   walks `CLASS` and `LIST` (and `MAP`) element kinds and masks nested `@IhawuResource` objects / collection
   elements through the registry. Explicitly tested, since this is the sharpest correctness risk.

3. **Per-call context is thread-local, with a coroutine→thread-local bridge.** The spike disproved a pure
   `CoroutineContext` element: `JsonTransformingSerializer.transformSerialize` is **synchronous**, called
   deep inside `Json.encodeToString`, so it cannot read `coroutineContext` (that needs a suspend function).
   The context (principal + `memoize`) is therefore stashed in a thread-local around the synchronous encode,
   installed by a small helper API. For Ktor (#82, coroutine-based), the bridge is
   `ThreadLocal.asContextElement()`. JS/native are single-threaded, so the holder is a plain reference. This
   requires an **`expect`/`actual`** holder (JVM thread-local; JS/native plain). Scoping to a single
   encode call — not an ambient holder — keeps cross-request contamination out, matching ADR 0001's intent.

4. **A multiplatform `ihawu-kotlinx` module** (jvm + js) — this is where non-JVM masking ships.

### Why not the custom-`Encoder` approach (deferred)
A custom `Encoder`/`SerializationStrategy` could stream (no `JsonElement` materialization) and avoid the
allocation cost, but it fights kotlinx's compile-time design and is materially harder for nested and
polymorphic types. It is **deferred**: pursue it only if a JMH benchmark on realistic payloads shows the
tree materialization actually hurts. The spike's nanoTime numbers were too noisy to justify it.

## Performance

The honest tradeoff: unlike the Jackson path (streaming writer-wrapping, no hot-path allocation), the
kotlinx path **materialises the whole `JsonElement` tree and rewrites it**, which costs extra allocation.
A JMH benchmark ships in the module comparing both backends on a representative payload.

**Benchmark results** (rough harness in `ihawu-kotlinx`'s `jvmTest` — warmup + 50k iterations × 3 runs,
per-thread allocation via `ThreadMXBean`; **not JMH** — a JMH module is a follow-up. Payload: an employee
with masked fields, a nested resource, and a map of resources):

| Backend | Throughput (steady state) | Allocation |
| --- | --- | --- |
| `ihawu-jackson` (streaming) | ~225–254k ops/s | ~2.9 KB/op |
| `ihawu-kotlinx` (JsonElement rewrite) | ~103–110k ops/s | ~6.6 KB/op |

The kotlinx path is **~2–2.5× slower** and allocates **~2.3× more** than the Jackson streaming path — the
expected cost of materialising the whole element tree before rewriting it. That tradeoff buys **non-JVM
masking**; if it ever bites on a hot path, the deferred custom-`Encoder` approach is the lever. Numbers are
directional (nanoTime, warm JVM); a future JMH run is authoritative.

## Consequences

| Concern | Outcome |
| --- | --- |
| Non-JVM masking | Real — the JS target masks through the same neutral SPI (KMP payoff). |
| Nested / collections / maps | Masked via registry-driven recursion; explicitly tested. |
| Polymorphic / sealed `@IhawuResource` | **Not supported in v1** — documented limitation, tracked as a follow-up. |
| Per-call context | Thread-local (JVM) + `ThreadLocal.asContextElement()` bridge for coroutines/Ktor; `expect`/`actual` holder. |
| `@IhawuResource` coupling | Stays kotlinx-free; kotlinx resources use the explicit registry. |
| Performance | Allocates more than the Jackson streaming path; JMH numbers published. |
| SPI | Reused unchanged — validates the #77 serialization-neutral contract across a second engine. |

### Out of scope (follow-ups)
- Polymorphic/sealed `@IhawuResource` recursion.
- The custom-`Encoder` streaming backend (perf), if a benchmark ever justifies it.
- Annotation-driven kotlinx resource discovery (a `@SerialInfo` companion), if ergonomics demand it.
