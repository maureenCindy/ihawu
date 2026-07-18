# ADR 0010 — Sealed polymorphic `@IhawuResource` masking on kotlinx.serialization

- **Status:** Accepted
- **Date:** 2026-07-18
- **Relates to:** #101 (polymorphic/sealed `@IhawuResource` in the kotlinx backend)
- **Depends on:** [ADR 0008](0008-kotlinx-serialization-masking.md) (kotlinx backend), [ADR 0006](0006-serialization-neutral-masking-spi.md) (neutral SPI)
- **Supersedes (in part):** [ADR 0008](0008-kotlinx-serialization-masking.md) — its "polymorphic/sealed not supported in v1" limitation, for the **sealed** case.

## Context

[ADR 0008](0008-kotlinx-serialization-masking.md) shipped the kotlinx backend with manual, registry-driven
recursion over `CLASS`/`LIST`/`MAP` — and an explicit limitation: **polymorphic/sealed `@IhawuResource`
hierarchies were not masked**. A polymorphic value's descriptor kind is `PolymorphicKind.SEALED`/`OPEN`, so
`MaskingJsonTransformer.maskElement` fell to its `else` branch and the value passed through unmasked (a real
leak if a sealed subtype carried sensitive fields). This ADR records the decision that closes that gap for
the **sealed** case; a spike (behind #101, isolated worktree) proved it on JVM and JS.

## Decision

Extend `MaskingJsonTransformer` to mask **sealed** `@IhawuResource` subtypes, **preserving the class
discriminator**, reusing the neutral SPI (`MaskingEngine.decide`) with **zero core changes** — the same
seam that carried #81/#82. **OPEN (non-sealed abstract/interface) polymorphism stays unsupported** and is
now explicitly tested as passthrough (see Scope).

Four sub-decisions, each spike-validated (kotlinx 1.8.1):

1. **Resolve the concrete subtype from the descriptor, not the `SerializersModule`.** A `SEALED` descriptor
   has `elementsCount == 2`: element 0 is the discriminator (`type`, `String`), element 1 is the value slot
   (kind `CONTEXTUAL`, serialName `kotlinx.serialization.Sealed<Base>`) whose **child element descriptors are
   the concrete subtypes**, each with `serialName == @SerialName` — which is exactly the discriminator value
   in the JSON. So resolution is: iterate `getElementDescriptor(1)`'s children and match `serialName`. **No
   `SerializersModule` and no base `KClass` needed.** We iterate `getElementDescriptor(i)` over
   `elementsCount` rather than the `elementDescriptors` extension, because that extension is
   `@ExperimentalSerializationApi` and would force an opt-in on consumers.

2. **Preserve the discriminator by treating its key as a passthrough.** The discriminator key is **not** an
   element of the subtype descriptor (`getElementIndex("type")` → `UNKNOWN_NAME`, and `getElementDescriptor`
   would then throw). So the masking loop copies the discriminator entry verbatim and masks the remaining
   fields as a `CLASS` against the subtype descriptor. Both polymorphic encodings are handled: the default
   object form `{"type":"circle", …payload}` and the array form `["circle", {…payload}]`
   (`useArrayPolymorphism = true`), where the payload object carries no discriminator.

3. **Thread the discriminator key through the factory.** The transformer gains a
   `classDiscriminator: String = "type"` parameter (backward-compatible default), plumbed through
   `maskingSerializer(...)`. **`Json.configuration.classDiscriminator` is not `@ExperimentalSerializationApi`
   in kotlinx 1.8.1** (verified: no opt-in, no deprecation on `getConfiguration()`/`getClassDiscriminator()`),
   so an adapter can pass it directly. The **Ktor adapter must thread
   `json.configuration.classDiscriminator`** rather than rely on the `"type"` default — see Consequences on
   the fail-open coupling. (Per-type `@JsonClassDiscriminator` and `classDiscriminatorMode` are not handled;
   follow-up.)

4. **OPEN polymorphism is out of scope for v1, and tested as passthrough.** OPEN subtypes are not
   descriptor-enumerable — masking them would need a `serializersModule.getPolymorphic(baseClass, value)`
   lookup, dragging the module + base `KClass` into the transformer. That is deferred (tracked as a
   follow-up). Crucially, an OPEN subtype passes through **even when a policy exists for it** — proven by a
   test — so the limitation is explicit, not silent.

## Consequences

| Concern | Outcome |
| --- | --- |
| Sealed `@IhawuResource` masking | Masked, discriminator preserved; object + array encodings. Tested on JVM **and** JS. |
| Nested / list of sealed subtypes | Masked for free — `maskElement` already threads the right element descriptor, so a polymorphic field/list-element reaches the new branch and recurses. |
| OPEN (abstract/interface) polymorphism | **Still unsupported** — passthrough, even with a matching policy; tested, not silent. A documented follow-up. |
| Discriminator-key coupling | **Fail-open** if the transformer's `classDiscriminator` disagrees with the encoding `Json`'s key: masking is silently skipped (tested). Adapters must thread `json.configuration.classDiscriminator`. |
| Core / SPI | Reused unchanged — polymorphism masks through `MaskingEngine.decide`; validates the #77 seam a third time. |
| Array-poly cast posture | `maskPolymorphicArray` trusts kotlinx's guaranteed `[String, Object]` shape (hard casts) — safe because the transformer only ever receives kotlinx's own serializer output for the delegate. |

### Out of scope (follow-ups)
- **OPEN (non-sealed abstract/interface) polymorphism** via `SerializersModule` lookup.
- Per-type `@JsonClassDiscriminator` and `classDiscriminatorMode` (e.g. `POLYMORPHIC` / `NONE`).
