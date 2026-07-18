# Package org.ihawu.ktor

The Ktor server adapter for Ihawu masking — the zero-wiring experience the Spring Boot starter gives
Spring, for Ktor apps. It builds on the multiplatform `ihawu-kotlinx` backend and the
serialization-neutral `ihawu-core` engine (ADR 0009); it adds no new engine.

### Entry point

- [IhawuKtor] — the Ktor application plugin. A single `install(IhawuKtor) { … }` registers a masking
  `ContentNegotiation` converter and installs the per-call masking context, so every
  `@IhawuResource` response is masked with no per-route code.
- [IhawuKtorConfig] — the plugin DSL: the principal bridge (`resolvePrincipal`), the field-policy
  source (`policies` / `policyResolver`), and the resource registry (`resources`).

### Extension points

- [MaskingContentConverter] — the `ContentConverter` that wraps each response type's serializer with
  the kotlinx masking serializer. Registered for you by [IhawuKtor]; exposed for advanced setups that
  drive `ContentNegotiation` themselves.
- [ihawuPrincipal] — the default identity bridge: reads an [org.ihawu.core.policy.IhawuPrincipal] from
  the call's `Authentication` result. Fail-closed — no authenticated principal yields `null`, which
  masks the whole resource.
