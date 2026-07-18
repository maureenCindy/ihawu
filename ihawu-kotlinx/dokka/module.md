# Package org.ihawu.kotlinx

The kotlinx.serialization backend for Ihawu masking — the *write* half of the engine for
`kotlinx.serialization`, and the multiplatform one (JVM + JS). It executes the decisions the
serialization-neutral `ihawu-core` engine makes, at kotlinx's serialization boundary.

Because kotlinx generates serializers at compile time (no per-property hook), masking works by letting
a value serialize to a `JsonElement` and rewriting it per the engine's decision, recursing into nested
`@IhawuResource` objects, lists, and map values through an explicit registry. See ADR 0008.

### Entry point

- [maskingRegistry] — build the `serialName → resourceName` registry from resource serializers.
- [maskingSerializer] — wrap a resource serializer so it masks through the engine, recursing via the registry.
- [IhawuKotlinxJson] — encode a value for a given `IhawuPrincipal`; a missing principal fails closed to `{}`.

### Limitations (v1)

- Polymorphic / sealed `@IhawuResource` hierarchies are not masked (their elements pass through) — a
  documented follow-up.
