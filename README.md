# Ihawu
Ihawu is a policy enforcement and dynamic data-masking engine built in **Kotlin (JVM)**, with first-class support for
**Spring Boot**. Ktor and Kotlin Multiplatform are design goals, not near-term releases — see
[Roadmap](#roadmap) below. 

---

## Why Ihawu?

The developer community is plagued by **security boilerplate leakage**. Infrastructure tools like Keycloak can tell you
*who* a user is (Authentication), and engines like Open Policy Agent (OPA) can tell you *what* rules apply 
(Authorization Decisions). However, neither tool can touch your application code nor interact with a JVM network socket
stream.

As a result, developers are forced to contaminate clean business logic with manual `if/else` visibility checks or 
maintain dozens of near-identical Data Transfer Objects (DTOs) like `UserFullResponse` and
`UserMaskedResponse` across microservices just to mask a credit card or hide a salary field.

**Ihawu shifts access control out of your code and into a declarative data layer.** It does not compete with identity
providers or policy brains. It acts as the missing **Policy Enforcement Point (PEP)** that executes their decisions 
transparently at the serialization boundary.

---

## How It Works

Ihawu operates as an automated, stateless data filter embedded natively inside your web application's ingress and 
egress lifecycles:
1. Ingress HTTP Request ────► Framework validates OAuth2 JWT (Keycloak)
2. Ihawu Adapter    ──► Maps raw claims into IhawuPrincipal
3. ResourcePolicyResolver ──► Resolves this caller's field policies for the resource
4. Controller Logic Runs ──► Returns raw, complete Database Entity class 
5. Ihawu Masker    ──► Intercepts Jackson (kotlinx.serialization planned)
6. Outbound JSON Stream ──► Transparently stripped (HIDE) or obfuscated (REDACT)

Step 3 goes through the `ResourcePolicyResolver` SPI. Two implementations ship: `RoleBasedResourcePolicyResolver`
for static role-based rules, and — on Spring Boot — `ConfigResourcePolicyProvider`, which binds rules straight
from `ihawu.policies` configuration. Either can be wrapped in `CachingResourcePolicyResolver`. To source rules
from somewhere else — a database, a per-tenant service, OPA — you implement the SPI; Ihawu does not ship those
integrations.

### Guiding principles
1. **Ingress Capture:** Your host framework (Spring Boot today; Ktor planned) handles the network cryptography and token 
verification. Ihawu instantly captures that verified identity and converts it into a uniform, framework-neutral 
`IhawuPrincipal`.
2. **Stateless Processing:** Your business controller runs completely unpolluted, querying your application database
and returning its raw, strongly-typed domain entity exactly as it is.
3. **Transparent Egress Masking:** Right before that object is written to the network pipe as a JSON string, 
Ihawu intercepts the serialization engine (Jackson today; `kotlinx.serialization` planned). It references your active policy matrix
and dynamically drops (`HIDE`) or overwrites (`REDACT`) restricted fields on the fly.

---

## What Makes Ihawu Better?

* **Zero-Friction Coexistence:** Let Keycloak handle passwords, UI login screens, and MFA. Let OPA evaluate complex 
corporate-wide Rego files. Ihawu takes the identity your framework has already verified and the decisions your policy
source returns, and handles the framework-specific task of enforcing them on the way out. It is a Policy Enforcement
Point, not a second decision engine.
* **Fail-Closed by Default:** If no verified principal is attached, or a policy lookup fails, Ihawu masks every field
of the resource rather than serializing it — you get an empty JSON block `{}`, not a leak. Forgetting to wire Ihawu up
produces an empty object; with hand-rolled masking, forgetting produces a leak. The failure modes run in opposite
directions.
* **No Reflection on the Hot Path:** Property writers are wrapped once per type, while Jackson builds that type's
serializer — not per request. Policies are resolved once per (call, resource) and memoized for the rest of the write,
so a response containing a thousand instances of a resource resolves its policy once. There is no runtime reflection
on the serialization path. (No published benchmark yet — see #71.)

---

## Why Not Just `@JsonView` or `@JsonFilter`?

Fair question, and for a small enough problem the answer is: you should. Jackson ships both, and Ihawu is built
**on** Jackson's serializer SPI rather than as an alternative to it.

* **`@JsonView`** — if you have two or three fixed response shapes known at compile time, this is the right tool
  and Ihawu is overkill. It cannot substitute a value, though, so a redacted `***-**-****` is not expressible.
* **`@JsonFilter`** — dynamic, and much closer to what Ihawu does. Read honestly, `ihawu-core` is a policy-driven,
  principal-aware `@JsonFilter` with the wiring and the policy model supplied for you.

What you would build yourself to get there: a `PropertyFilter` subclass to substitute placeholders rather than
merely drop fields; a `MappingJacksonValue` wrapper at every handler, where the one you forget is the one that
leaks; policy plumbing to load rules from config, a database, or OPA; and manual assembly of the filter set for
nested types and collections. Ihawu supplies those, registers once on the `ObjectMapper`, recurses automatically,
and fails closed when identity is missing.

Full comparison — including masking in the database, which is stronger than Ihawu wherever it applies:
[Comparison](https://ihawu.org/concepts/comparison/).

---

## What Ihawu Does Not Protect

Ihawu enforces at **one** exit: an `ObjectMapper` with `IhawuModule` registered, serializing a call that has an
`IhawuPrincipal` attached. Your object still travels through the application in full, and nothing masks it on any
other path out of the process. It is **not** masked when it is written to a log, published to Kafka, written to a
cache, exported to CSV, rendered into a server-side template, or serialized by a second `ObjectMapper` without the
module registered.

That is inherent to enforcing at the serialization boundary, and it is a deliberate trade — masking where the
response is written is what lets your controllers return whole, truthful domain objects. But it means Ihawu masks
**API responses**, not "sensitive data" in general. If an SSN must never appear in a log line, Ihawu is not what
stops it.

Treat Ihawu as last-mile, defense-in-depth enforcement, layered with controls that act closer to the data —
column-level grants, row-level security, vendor dynamic masking — which are stronger wherever they apply, because
the value never reaches your JVM at all. Full threat model:
[What Ihawu does not protect](https://ihawu.org/concepts/how-it-works/#what-ihawu-does-not-protect).

---

## Installation

Add the Spring Boot starter — it pulls `ihawu-core` transitively, so it's the only dependency you add.

**Gradle (Kotlin DSL)**
```kotlin
implementation("org.ihawu:ihawu-spring-boot-starter:0.1.0")
```

**Gradle (Groovy DSL)**
```groovy
implementation "org.ihawu:ihawu-spring-boot-starter:0.1.0"
```

**Maven**
```xml
<dependency>
    <groupId>org.ihawu</groupId>
    <artifactId>ihawu-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Not on Spring? Depend on `org.ihawu:ihawu-jackson` (it pulls `ihawu-core` transitively), register
`IhawuModule` on your `ObjectMapper`, and drive masking through a `ResourcePolicyResolver`. Full guides
at [docs.ihawu.org](https://docs.ihawu.org).

Not on the JVM, or on `kotlinx.serialization`? `org.ihawu:ihawu-kotlinx` is a **multiplatform** (JVM +
JS) backend: register a masking serializer per `@IhawuResource` type and encode via `IhawuKotlinxJson`.
It reuses the same neutral engine, so masking behaves identically — see
[ADR 0008](docs/adr/0008-kotlinx-serialization-masking.md) for its design and performance tradeoffs.

On Ktor? `org.ihawu:ihawu-ktor` gives Ktor apps the same zero-wiring experience as the Spring Boot
starter: a single `install(IhawuKtor) { … }` masks every `@IhawuResource` response per the caller's
role. It builds on `ihawu-kotlinx` and reuses the same neutral engine — see
[ADR 0009](docs/adr/0009-ktor-adapter.md) and the runnable `samples/ktor-sample`.

> **Migrating from 0.2.0.** The Jackson engine moved out of `ihawu-core` into a new `ihawu-jackson`
> artifact, so `ihawu-core` itself is now serialization-neutral. Direct `ihawu-core` users who registered
> `IhawuModule` — or loaded config via `RoleBasedResourcePolicyResolver.fromJson` (now
> `JacksonPolicyConfig.fromJson`) — must add `org.ihawu:ihawu-jackson`. **Spring Boot starter users are
> unaffected**; the starter pulls it in.

---

## The Developer Experience

With Ihawu, your business service controllers remain clean, explicit, and unpolluted by authorization rules. 
You return your raw, strongly-typed database records directly:

### 1. Define Your Target Domain Model

Declare every maskable field nullable — a masked field is either omitted (`HIDE`) or set to `null` (`REDACT`
on a non-`String`), so its type has to permit that. (`String` fields may stay non-nullable — they redact to a
placeholder.)

```kotlin
@IhawuResource(name = "UserProfile")
data class UserProfile(
    val userId: String,
    val fullName: String,
    val email: String,
    val socialSecurityNumber: String?,
    val performanceReviewNotes: String?
)
```

> **Masking respects your type contract.** A masked response still has to deserialize into the type it claims
> to be, so what a strategy may do depends on how the field is declared. `REDACT` writes a placeholder on a
> `String`, and `null` on any other **nullable** field — so a numeric or boolean field is redactable only when
> it is nullable, never to a fake `0` or `false`. `HIDE` omits the field, so it too needs a **nullable/optional**
> field. A non-nullable non-`String` field has no honest masked form; declare it nullable to mask it. On Spring
> Boot these rules are checked **at startup** — a policy that cannot be honoured fails the application context,
> naming the field ([ADR 0005](docs/adr/0005-hard-fail-on-unenforceable-masking-policy.md)).

### 2. Declare Your Policies

On Spring Boot, bind the rules straight from configuration — no code:

```yaml
ihawu:
  policies:
    - resource: UserProfile
      roles:
        EMPLOYEE:
          - field: socialSecurityNumber
            strategy: REDACT
            placeholder: "***-**-****"
          - field: performanceReviewNotes
            strategy: HIDE
```

For rules that live elsewhere — a database, a per-tenant service, OPA — implement the
`ResourcePolicyResolver` SPI and register it as a bean. Ihawu calls it; it does not care where your rules
come from.

So for an `EMPLOYEE` requesting this object:
* **`socialSecurityNumber`** ──► Strategy: `REDACT` (Placeholder: `"***-**-****"`)
* **`performanceReviewNotes`** ──► Strategy: `HIDE` (Removes property entirely)

### 3. The Transparent Output Result
When an unauthorized user makes an HTTP request, Ihawu automatically transforms the serialization stream. 
The client safely receives:

```json
{
  "userId": "usr_9482",
  "fullName": "Jane Doe",
  "email": "jane.doe@company.com",
  "socialSecurityNumber": "***-**-****"
}
```

---

## Operability

Ihawu **fails closed**: when it cannot mask safely it omits fields rather than leak them, so a resource
serializes as `{}` (or a partial object) with an HTTP **200**. That is the correct default — but it makes
an empty `{}` **ambiguous**: it can mean *"fully masked for this caller"* **or** *"policy resolution
failed and Ihawu fell back to masking everything."* The two are byte-identical on the wire.

Until richer signals land (below), tell them apart from the **logs** — every fail-closed decision is
logged with the resource name, never the protected value:

| Level | When | Message |
| --- | --- | --- |
| `WARN` | No `IhawuPrincipal` on the serialization call | *"No IhawuPrincipal attached … serialization failing closed for resource '…'"* |
| `ERROR` | The resolver threw (misconfig or policy-store outage) | *"Ihawu masking failed for resource '…', serialization failing closed"* |
| `ERROR` | A field's policy can't be honoured at runtime (`HIDE` on a non-nullable field, or `REDACT` on a non-nullable non-`String`) | *"Ihawu HIDE on required field …"* / *"Ihawu REDACT on non-nullable non-String …"* |

**Alert on the resolver-failure `ERROR`.** A sustained rate of it means your policy source is down and
every `@IhawuResource` is degrading to `{}` — an outage wearing a `200`, not normal masking. (The runtime
type-contract `ERROR`s should be rare: statically-configured policies are rejected at startup
([ADR 0005](docs/adr/0005-hard-fail-on-unenforceable-masking-policy.md)); they only reach runtime from a
dynamic resolver.)

A Micrometer failure metric (`ihawu.masking.failures`) and an optional `fail-request` mode — turning a
resolver outage into a `5xx` your monitoring already understands — are planned for the
serialization-neutral core (#89).

---

## Executable Documentation Philosophy

Following the strict code documentation principles engineered by the JetBrains standard library team, all documentation
code samples in Ihawu are **not static Markdown strings**. Every reference code block lives inside our runnable test 
suites within the `/samples` project subdirectory.

Dokka matches these targets via the `@sample` compiler tag. This guarantees that if our API layout ever shifts, 
our public user documentation breaks at compile time during CI verification builds. Your guides are guaranteed to 
be 100% accurate and functional forever.

---

## Roadmap

Work is tracked in [GitHub milestones](https://github.com/maureenCindy/ihawu/milestones). Three, in order:

* **Docs: claims match behaviour** — every claim in the README, CONTRIBUTING, and the docs site is either true or
  removed. No feature described that does not exist.
* **0.2.0 — Type-correct masking** — masked output must satisfy the declared type contract. Type-aware masking
  (`REDACT` a `String` to a placeholder or any nullable field to `null`; `HIDE` an optional field), with policies
  that cannot be honoured failing at startup (#67, merging #68; [ADR 0005](docs/adr/0005-hard-fail-on-unenforceable-masking-policy.md)),
  and observable fail-closed behaviour (#72). Breaking: masking a non-`String` (or any `HIDE`) field now requires
  it to be nullable, and `MaskingStrategy.defaultValue` is renamed `defaultPlaceholder`.
* **0.3.0 — Serialization-neutral core** — lift the enforcement point off Jackson onto a serialization-neutral SPI.
  This is what unlocks Kotlin Multiplatform and a Ktor adapter, and it is the same seam that would let Ihawu mask at
  sinks other than HTTP JSON.

On multiplatform, specifically: `ihawu-core` depends on `jackson-databind` and `slf4j-api`, both JVM-only, and the
masking engine is built directly on Jackson's serializer SPI. KMP is therefore not a target you can add to the build
— it needs the 0.3.0 refactor first. `kotlinx.serialization` also has no equivalent of Jackson's
`BeanSerializerModifier`, so that backend is a second engine rather than a port. The full explanation is on the docs
site: [Running beyond the JVM](https://ihawu.org/core/overview/#running-beyond-the-jvm).

---

## Contributing

We love open-source contributions! Ihawu uses interactive GitHub Issue Forms to streamline triage and project board
management. 

To get started:
1. Fork the repository and explore our runnable test applications under `/samples`.
2. Check our GitHub issue board for cards labeled `good-first-issue`.
3. Ensure that any logic changes to `ihawu-core` include a compiling code sample update within the `samples` 
directory to maintain self-testing integrity.

For a comprehensive layout of code styles and PR submission steps, see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License
Ihawu is open-source software licensed under the **Apache License 2.0**.