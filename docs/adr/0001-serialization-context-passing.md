# ADR 0001 — How the principal reaches the masking serializer

- **Status:** Accepted
- **Date:** 2026-06-24
- **Relates to:** #20 (serialization interceptor), #22 (request-scoped caching), #23 (fail-closed)
- **Depends on:** #16 (`@IhawuResource`, `FieldPolicy`, `MaskingStrategy`), #17 (`ResourcePolicyResolver`, `IhawuPrincipal`)

## Context

Ihawu masks `@IhawuResource` object graphs at the Jackson serialization boundary. The runtime
heart of this (#20) is a `BeanSerializerModifier` that wraps the serializer for any
`@IhawuResource` type and applies the resolved `List<FieldPolicy>` (`HIDE` omits a field,
`REDACT` substitutes a placeholder).

To resolve policies, the serializer needs the caller's identity — an `IhawuPrincipal`. But a
Jackson serializer is a **single, shared, thread-safe object created once at startup**, while
the principal is **per request**. The core design question for #20 is therefore:

> How does per-request identity reach a shared serializer at the instant it writes bytes?

This decision is security-critical: the library's entire promise is that *the wrong caller
never sees protected data*. It must therefore be evaluated by its worst-case failure mode, not
its happy path.

### The request lifecycle (where the problem lives)

Tracing `GET /employees/42/profile` in a Spring Boot consumer (the core itself stays
framework-neutral):

1. Request arrives with an `Authorization` header.
2. Spring Security authenticates and stores an `Authentication` in `SecurityContextHolder`
   (itself a request-bound `ThreadLocal`).
3. The controller runs on **complete, truthful data** — loads the full `Employee`, maps it to
   an `EmployeeProfile` DTO annotated `@IhawuResource("employee.profile")` with nested
   `Address` / `Compensation` (each their own `@IhawuResource`).
4. Spring hands the DTO to the application `ObjectMapper` via
   `MappingJackson2HttpMessageConverter`.
5. **Ihawu intercepts here.** Our `BeanSerializerModifier` means the serializer for
   `EmployeeProfile` is our masking serializer. As it writes, it must: identify the caller →
   resolve their policy for `"employee.profile"` → `HIDE`/`REDACT` fields → mask nested
   resources the same way.
6. Masked JSON is written to the response.

Step 5 is the bridge this ADR decides.

## Options considered

### Option A — Jackson per-call attribute (`writer().withAttribute(...)`)

The framework adapter attaches the principal to a single serialization call:
`mapper.writer().withAttribute(KEY, principal).writeValue(out, dto)`. The serializer reads
`provider.getAttribute(KEY)`. The value lives on the `SerializerProvider` for that one call and
is discarded when the call ends.

### Option B — `ThreadLocal` holder in core

A `ThreadLocal<IhawuPrincipal>` in `ihawu-core`. A Spring filter sets it at request start and
clears it in `finally`; the serializer reads ambient `IhawuContext.current()`.

### Option C — pre-resolve, attach a finished policy map

Resolve all policies before serialization and attach `Map<resourceName, List<FieldPolicy>>`,
making the serializer a dumb lookup by `@IhawuResource.name`.

## Analysis

### Security (decisive)

Each option is judged by its worst-case failure mode.

- **Option B is catastrophic in the worst case.** A `ThreadLocal` is correct only if always
  cleared *and* only if serialization runs on the thread that set it.
  - **Leaked cleanup:** threads are pooled. Any path that skips the `finally` clear (an
    exception in the wrong place, filter-ordering surprises, another library wrapping the
    chain) lets request B reuse the thread and inherit request A's principal — serving data
    masked as the wrong identity, or leaking unmasked data under a stale admin role. A
    confidentiality breach caused by a missing `remove()`.
  - **Thread hops:** WebFlux, coroutines, `@Async`, `CompletableFuture` — once serialization
    lands on a different thread, the `ThreadLocal` is either empty (annoying fail-closed `{}`)
    or, on a pooled worker, holds someone else's principal (a breach).
  - The flaw is not that B *cannot* be made correct — it is that B's correctness is
    **conditional on discipline a library cannot enforce in its consumers**.

- **Option A is correct by construction.** The principal is bound to one serialization call and
  cannot outlive it. There is no shared or thread-bound location to leak through and no
  `finally` to forget — cross-request contamination is **structurally impossible**. It behaves
  identically on MVC, WebFlux, coroutines, and batch jobs because it never asks "what thread am
  I on?"

- **Option C** is safe on the cross-request axis (it is A with a different payload) but
  introduces an **under-coverage** failure mode: the caller must enumerate every
  `@IhawuResource` in the graph up front; a missed nested type serializes **unmasked**. It also
  forces graph-walking before serialization — the reflection we are trying to avoid.

### Performance

- **Reading the principal** is a wash: A is a lookup on a per-call object, B is
  `ThreadLocal.get()`. Both negligible. This is not where performance is won.
- **The real lever is not re-resolving.** A list endpoint serializing 200 `EmployeeProfile`s
  must not call the resolver 200× (or 600× with nested types) — that is #22's job. The
  transport choice affects how cleanly #22 slots in:
  - With **A**, the `SerializerProvider` attributes are already a per-call scratchpad. The
    first resolve can stash its result there and reuse it for the rest of the response — a
    request-scoped cache that is **automatically leak-free** because it dies with the call.
  - With **B**, the cache is more thread-bound state with the *same* cleanup hazards — two
    things that can leak instead of one.
- **No hot-path reflection** is independent of the choice: reuse Jackson's already-cached
  `BeanPropertyWriter[]` (property metadata is resolved once per type and cached). Do not roll
  custom reflection.

### Cost of the chosen option

Option A requires the Spring starter to set the attribute per response — a small custom
`HttpMessageConverter` (or a `writer().withAttribute(...)` wrapper) rather than the untouched
default converter. That cost lands in the **framework adapter, exactly where framework glue
belongs**, keeping the security-critical core simple and structurally safe. It is a
well-established Spring pattern.

## Decision

**Adopt Option A: Jackson `SerializerProvider` attributes.** The masking serializer resolves
policy lazily per bean by reading the principal from the per-call attribute, and memoizes each
resolved `List<FieldPolicy>` back into the same call-scoped attributes for reuse within the
response.

Rationale, in the order the community will weigh it:

1. **Security** — cross-request contamination is impossible by construction, not by cleanup
   discipline. For a security tool, "impossible by design" beats "safe if everyone is careful."
2. **Performance** — equal on the cheap part (reading identity), strictly better on the part
   that matters (request-scoped caching composes safely and leak-free).
3. **Correctness across runtimes** — identical behavior on MVC, WebFlux, coroutines, `@Async`,
   and batch jobs.
4. **Testability / trust** — every test and sample is a framework-free, transparent
   `mapper.writer().withAttribute(...).writeValueAsString(...)`; a reviewer sees exactly which
   principal produced which output.

## Consequences

### Data flow

```
SecurityContext (Authentication)
   │  starter maps to normalized identity (#17 IhawuPrincipal)
   ▼
writer().withAttribute(PRINCIPAL, ihawuPrincipal)          ← Spring starter (later issue)
   │
   ▼
Jackson writeValue → MaskingBeanSerializer.serializeFields
   │   read principal from provider attribute
   │   resolve once per resource, memoize in attributes      ← #22 seam
   │   HIDE / REDACT per cached BeanPropertyWriter
   │   nested @IhawuResource → same serializer (free recursion)
   │   no principal / resolve throws → {}                    ← #23 seam
   ▼
masked JSON
```

### Implications

- **#20** reads the principal from `provider.getAttribute(...)`; nested resources and
  collection items are masked automatically because each annotated type independently gets the
  wrapped serializer (recursion is free — no manual graph walker).
- **#22** formalizes the per-call memoization already enabled by the attribute scratchpad.
- **#23** has a single, clean choke point: principal absent or resolution/masking throws → emit
  `{}`.
- The **Spring starter** owns mapping `Authentication` → `IhawuPrincipal` and setting the
  per-response attribute via a custom message converter.

### Test example

```kotlin
val json = mapper.writer()
    .withAttribute(IhawuAttrs.PRINCIPAL, IhawuPrincipal("u1", setOf("MANAGER"), emptyMap()))
    .writeValueAsString(employeeProfile)
// assert salary REDACTED, ssn HIDDEN, nested address masked
```

## Alternatives rejected

- **Option B (`ThreadLocal`)** — rejected: worst-case failure is a confidentiality breach, its
  correctness depends on cleanup discipline a library cannot enforce, and it misbehaves on
  async/reactive runtimes.
- **Option C (pre-resolved map)** — rejected: introduces an under-coverage leak (missed nested
  types serialize unmasked) and forces pre-serialization graph-walking. It is also not an
  independent transport — it still relies on A or B to carry the payload.