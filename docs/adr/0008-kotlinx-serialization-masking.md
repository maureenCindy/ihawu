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

> **Update — 2026-07-21 (#102): authoritative JMH numbers.** The table above is superseded by the JMH
> `benchmark` module (JMH 1.37, `-prof gc`, 2 forks × 5×1s warmup + 5×1s measurement; Intel i7-7820HQ
> 2.9 GHz, Corretto JDK 17.0.9). `small` = one employee (masked fields, a nested resource, a map of
> resources); `large` = a list of 100. Plain-serialization baselines included — the number the old harness
> could not give.
>
> | Benchmark | small ops/s | small B/op | large ops/s | large B/op |
> | --- | --- | --- | --- | --- |
> | `plainJackson` | 1,156,948 ± 46,624 | 800 | 14,637 ± 322 | 21,928 |
> | `ihawuJackson` | 397,886 ± 89,357 | 2,864 | 4,277 ± 3,276 † | 169,832 |
> | `plainKotlinx` | 1,174,631 ± 25,564 | 480 | 11,866 ± 472 | 26,392 |
> | `ihawuKotlinx` | 233,909 ± 22,945 | 6,472 | 2,623 ± 44 | 573,872 |
>
> † wide interval (fork variance); treat that cell as indicative.
>
> What the JMH run corrects and confirms:
> - **The cross-backend gap is smaller than the interim numbers claimed:** masked kotlinx is **~1.6–1.7×**
>   slower than masked Jackson (not 2–2.5×), allocating ~2.3× (small) to ~3.4× (large) more.
> - **The interim allocation figures were accurate** (2.9 KB / 6.6 KB per op ≈ 2,864 / 6,472 B/op).
> - **New, from the baselines:** masking is not free on either backend — `ihawuJackson` runs ~2.9–3.4×
>   slower than plain Jackson and allocates ~2 KB/op over it (so "no hot-path allocation" above overstated
>   the streaming path; the *decision* layer allocates), and `ihawuKotlinx` runs ~4.5–5× slower than plain
>   kotlinx. Absolute cost stays small (~2.5 µs/op Jackson, ~4.3 µs/op kotlinx on the small payload).
> - The tradeoff and the lever are unchanged: the kotlinx tree rewrite buys non-JVM masking; the deferred
>   custom-`Encoder` remains the option if it ever matters on a hot path.
>
> Reproduce with `./gradlew :benchmark:jmh` (see `benchmark/README.md`).

## Consequences

| Concern | Outcome |
| --- | --- |
| Non-JVM masking | Real — the JS target masks through the same neutral SPI (KMP payoff). |
| Nested / collections / maps | Masked via registry-driven recursion; explicitly tested. |
| Polymorphic / sealed `@IhawuResource` | **Not supported in v1** — documented limitation. *(Superseded in part by [ADR 0010](0010-sealed-polymorphic-kotlinx-masking.md): sealed hierarchies are now masked; OPEN/abstract remains a follow-up.)* |
| Per-call context | Thread-local (JVM) + `ThreadLocal.asContextElement()` bridge for coroutines/Ktor; `expect`/`actual` holder. |
| `@IhawuResource` coupling | Stays kotlinx-free; kotlinx resources use the explicit registry. |
| Performance | Allocates more than the Jackson streaming path; JMH numbers published. |
| SPI | Reused unchanged — validates the #77 serialization-neutral contract across a second engine. |

### Out of scope (follow-ups)
- Polymorphic/sealed `@IhawuResource` recursion. *(Sealed done in [ADR 0010](0010-sealed-polymorphic-kotlinx-masking.md); OPEN/abstract still a follow-up.)*
- The custom-`Encoder` streaming backend (perf), if a benchmark ever justifies it.
- Annotation-driven kotlinx resource discovery (a `@SerialInfo` companion), if ergonomics demand it.
