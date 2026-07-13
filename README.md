# Ihawu
Ihawu is a unified, cross-framework policy enforcement and dynamic data-masking engine built in **Kotlin (JVM)** with
first-class support for **Spring Boot** (with **Ktor** support planned). 

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

Not on Spring? Depend on `org.ihawu:ihawu-core:0.1.0` directly and drive masking through a
`ResourcePolicyResolver`. Full guides at [docs.ihawu.org](https://docs.ihawu.org).

---

## The Developer Experience

With Ihawu, your business service controllers remain clean, explicit, and unpolluted by authorization rules. 
You return your raw, strongly-typed database records directly:

### 1. Define Your Target Domain Model

Declare every maskable field nullable — a hidden field is absent from the payload, so the type has to permit
its absence.

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

> **Masking has to respect your type contract.** A masked response still has to deserialize into the type it
> claims to be, so the strategy is constrained by how the field is declared. `HIDE` drops the field, so apply
> it only to a **nullable** field. `REDACT` substitutes a string placeholder, so today it is type-safe on
> **`String`** fields only — redacting a numeric field would write a string where the schema promises a number.
> In short: **`REDACT` a `String`, `HIDE` a nullable.** Both constraints are being lifted — see #67 and #68.

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

## Executable Documentation Philosophy

Following the strict code documentation principles engineered by the JetBrains standard library team, all documentation
code samples in Ihawu are **not static Markdown strings**. Every reference code block lives inside our runnable test 
suites within the `/samples` project subdirectory.

Dokka matches these targets via the `@sample` compiler tag. This guarantees that if our API layout ever shifts, 
our public user documentation breaks at compile time during CI verification builds. Your guides are guaranteed to 
be 100% accurate and functional forever.

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