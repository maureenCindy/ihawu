# Ihawu Spring Boot Starter

Spring Boot auto-configuration for [Ihawu](../README.md) — the cross-framework **Policy Enforcement
Point** that masks restricted fields at the serialization boundary, failing closed (`{}`) on error.
Add the starter to the classpath and Ihawu configures itself into your application; there is no
manual `ObjectMapper` or interceptor wiring to do.

> **Status — under active development.** This release establishes the auto-configuration foundation
> and the typed `ihawu.*` properties. Bean wiring (identity bridge, serializer/interceptor
> registration, policy-resolver and cache binding) lands in subsequent releases.

---

## Installation

The starter pulls `ihawu-core` transitively, so it is the only dependency you add.

**Gradle (Kotlin DSL)**
```kotlin
implementation("com.ihawu:ihawu-spring-boot-starter:<version>")
```

**Gradle (Groovy DSL)**
```groovy
implementation "com.ihawu:ihawu-spring-boot-starter:<version>"
```

**Maven**
```xml
<dependency>
    <groupId>com.ihawu</groupId>
    <artifactId>ihawu-spring-boot-starter</artifactId>
    <version>VERSION</version>
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

**`application.yml`**
```yaml
ihawu:
  enabled: true
```

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
