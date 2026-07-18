# Package org.ihawu.jackson

The Jackson backend for Ihawu masking — the *write* half of the engine. It executes the decisions the
serialization-neutral `ihawu-core` engine makes, at Jackson's serialization boundary.

### Entry point

- [IhawuModule] — the Jackson `Module` you register on an `ObjectMapper` to activate masking. It wires
  the neutral masking engine into Jackson via a `BeanSerializerModifier`, so every
  `@IhawuResource`-annotated type is masked with no per-handler code.
- [IhawuSerialization] — the per-call attribute key under which the caller's `IhawuPrincipal` is
  supplied for a single write (`ObjectWriter.withAttribute`).

### Startup validation & config

- [MaskingContractValidator] — checks configured policies against a resource's declared type contract
  using Jackson's introspection, so an unenforceable policy is caught up front rather than at runtime.
- [JacksonPolicyConfig] — loads a `RoleBasedResourcePolicyResolver` from JSON configuration.
