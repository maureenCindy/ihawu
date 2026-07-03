# Ihawu Spring Boot Starter

Spring Boot auto-configuration for [Ihawu](../README.md) — the cross-framework **Policy Enforcement
Point** that masks restricted fields at the serialization boundary, failing closed (`{}`) on error.
Add the starter to the classpath and Ihawu configures itself into your application; there is no
manual `ObjectMapper` or interceptor wiring to do.

> **Status — under active development.** The starter provides auto-configuration, the Spring Security
> identity bridge, Jackson serializer/interceptor registration, request-scoped policy caching, and
> static policy configuration via `ihawu.policies`. Dynamic policy sources and richer configuration
> land in subsequent releases.

---

## Installation

The starter pulls `ihawu-core` transitively, so it is the only dependency you add.

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

Pre-release builds are published as `-SNAPSHOT` to the Sonatype snapshots repository.

---

## Auto-configuration

The starter registers `IhawuAutoConfiguration` through
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Spring Boot 3.x —
**not** the legacy `spring.factories`). It activates automatically when the starter is on the
classpath and `ihawu.enabled` is not `false`.

Conventions the starter follows:

- **No component scanning** — every bean is declared explicitly with `@Bean`.
- **Overridable** — beans the starter contributes are guarded with `@ConditionalOnMissingBean`, so
  defining your own bean of the same type wins.
- **Master switch** — setting `ihawu.enabled=false` backs the whole integration off; Ihawu
  contributes nothing to your context.

---

## Configuration properties

Properties are bound from the `ihawu.*` namespace into a type-safe `IhawuProperties`, with IDE
auto-completion via the Spring configuration metadata.

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `ihawu.enabled` | `Boolean` | `true` | Master switch for the Ihawu integration. When `false`, the auto-configuration backs off entirely. |
| `ihawu.policies` | `List` | `[]` | Static masking rules: per resource, the field policies to apply for each role. Empty means nothing is masked. |

**`application.yml`**
```yaml
ihawu:
  enabled: true
```

---

## Static masking rules (`ihawu.policies`)

For simple, fixed rule sets you can declare masking policies directly in configuration — no code. Each
entry maps a **resource** (the `@IhawuResource` name) to the **field policies** applied for each
**role** the caller holds:

```yaml
ihawu:
  policies:
    - resource: employee            # matches @IhawuResource("employee")
      roles:
        ADMIN:
          - field: ssn
            strategy: REDACT        # replace the value…
            placeholder: "***-**-****"   # …with this (optional; omit to use the strategy default)
          - field: salary
            strategy: HIDE          # remove the field entirely
```

- **`strategy`** is `HIDE` (drops the field) or `REDACT` (replaces its value). Omitting it defaults to
  the stricter `HIDE`.
- **`placeholder`** applies only to `REDACT`; when omitted, the strategy's own default value is used.
- Policies **union across the caller's roles**; when two roles mask the same field, the stricter
  strategy wins.
- A field with **no matching rule stays visible** — masking is a denylist, so unconfigured fields are
  public by design. (Missing *identity* still fails closed; see the [ADRs](../docs/adr).)

Configuration is validated **eagerly at startup**: duplicate `resource` keys and blank `field` names
fail the application context rather than silently mis-masking at request time.

### Dynamic rules

`ihawu.policies` is the zero-code default. For rules that come from a database, OPA, or a per-tenant
service, supply your own `ResourcePolicyProvider` (static rules) or `ResourcePolicyResolver` (full
control) bean — it overrides the config-backed default via `@ConditionalOnMissingBean`. See
[ADR 0004](../docs/adr/0004-static-policy-configuration.md).

---

## How it fits in

Your host framework authenticates the request, your policy source decides the rules, and Ihawu
**enforces** them — dropping (`HIDE`) or obfuscating (`REDACT`) restricted fields as the response is
serialized, and failing closed rather than leaking on error. Ihawu does not replace your identity
provider or policy engine; it executes their decisions at the egress boundary. See the
[project README](../README.md) for the full architecture.

---

## License

Apache License 2.0 — see [LICENSE](../LICENSE).
