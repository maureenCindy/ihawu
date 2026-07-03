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
3. Policy Engine  ──► Fetches live rules (DB, Config, or OPA)
4. Controller Logic Runs ──► Returns raw, complete Database Entity class 
5. Ihawu Masker    ──► Intercepts Jackson (kotlinx.serialization planned)
6. Outbound JSON Stream ──► Transparently stripped (HIDE) or obfuscated (REDACT)

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
corporate-wide Rego files. Ihawu connects directly to them to handle the complex, framework-specific task of data 
masking within your application.
* **Fail-Closed by Default:** Security boundaries must be unbreakable. If an authentication or rule validation lookup
error occurs, Ihawu fails closed—returning an empty JSON block `{}` or dropping the response entirely rather than
leaking unauthorized data.
* **Reflective Safety without Performance Overhead:** Fields are evaluated using fast, cached property mappings rather
than slow runtime reflection loops on hot request paths, introducing a negligible latency overhead of less than 
50 microseconds per payload.

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
```kotlin
@IhawuResource(name = "UserProfile")
data class UserProfile(
    val userId: String,
    val fullName: String,
    val email: String,
    val socialSecurityNumber: String,
    val performanceReviewNotes: String
)
```

### 2. Configure Dynamic Policies via Admin UI (or File Local Config)
If a standard user requests this object, Ihawu evaluates your business dynamic data rules:
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