# Compatibility & Versioning Policy

From **1.0.0**, Ihawu follows [semantic versioning](https://semver.org) against the surface defined
below. The promise is mechanically enforced: every published module compiles under Kotlin **explicit API
mode**, and its public ABI is locked in a checked-in dump (`<module>/api/*.api`) verified by
binary-compatibility-validator on every build.

## What the promise covers

- **The public API of the published artifacts** — `org.ihawu:ihawu-core`, `ihawu-jackson`,
  `ihawu-kotlinx`, `ihawu-ktor`, `ihawu-spring-boot-starter` — exactly as recorded in their `.api` dumps.
- **Configuration property names** — the starter's `ihawu.*` properties and the Ktor plugin DSL members.
  Renaming or removing one is a breaking change.
- **Metric names and tags** — `ihawu.masking.failures{resource,reason}` and the `FailReason` tag values.
  Operators alert on these; they are contract.
- **Masking semantics** — the documented fail-closed behaviour and the type contract (ADRs 0003, 0005,
  0011). Tightening what is masked can be a minor; loosening it (masking *less*) is treated as breaking.

## What it does not cover

- Anything `internal`, and the unpublished modules (`samples/*`, `smoke-test`, `benchmark`).
- Declarations marked **[`@ExperimentalIhawuApi`](ihawu-core/src/commonMain/kotlin/org/ihawu/core/annotation/ExperimentalIhawuApi.kt)**
  — these require an explicit `@OptIn`, may change or be removed in any release without a deprecation
  cycle, and are excluded from the `.api` dumps. Graduation (removing the marker) is non-breaking.
- **Log message wording** and exception message text (log *levels* for the documented fail-closed events
  are kept stable on a best-effort basis).
- Exact JSON formatting beyond the documented masking semantics (key order, whitespace).
- Transitive dependency versions, beyond the baselines stated below.

## Deprecation cycle

Removals are announced by deprecation first: `@Deprecated` (with a replacement and a migration note in
the release notes) ships in a **minor** release; the removal happens no earlier than the **next major**.
Experimental API is exempt (see above).

## Dependency baselines (1.0, per [ADR 0012](docs/adr/0012-boot4-jackson3-strategy.md))

| Artifact | Baseline | Notes |
| --- | --- | --- |
| `ihawu-core` | Kotlin Multiplatform (JVM 17+, JS/IR) | No serialization or framework dependency. |
| `ihawu-jackson` | Jackson **2.x** (`jackson-databind`) | Jackson 2 is actively maintained; a Jackson 3 (`tools.jackson.*`) backend arrives as the additive `ihawu-jackson3` in 1.1. |
| `ihawu-kotlinx` | kotlinx.serialization 1.x (JVM + JS) | |
| `ihawu-ktor` | Ktor **3.x** | |
| `ihawu-spring-boot-starter` | Spring Boot **3.5.x** | **Boot 3.5 reached OSS end-of-life on 2026-06-30**; this starter line suits commercially-supported 3.5 estates. A Spring Boot 4 starter (on `ihawu-jackson3`) arrives as a separate additive artifact in 1.1 — Boot 4 mandates Jackson 3, so the existing starter cannot adopt it in place. |

Adding new artifacts (e.g. `ihawu-jackson3`, a Boot 4 starter) is a **minor**. Removing a published
artifact or changing the frozen core SPI is what would constitute a **2.0**; no removal is planned while
Jackson 2 remains maintained.

## Documentation versioning

The API reference at [docs.ihawu.org](https://docs.ihawu.org) tracks the latest release. From **1.0.0**
the generated docs are archived per version, and a version selector ships with **1.0.1** (#94) — so the
stable line is browsable per release, without the pre-1.0 churn.
