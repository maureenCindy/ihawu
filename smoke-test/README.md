# Published-artifact smoke tests

A **standalone multi-module** build whose modules consume the **published** `org.ihawu` artifacts from
`mavenLocal()` — with no project references — to prove the packaged jars are usable by real consumers.
It is deliberately **not** part of the root Gradle build (it has its own `settings.gradle.kts`), so
`./gradlew build` never touches it and the dev build stays fast.

## Why this exists

The in-reactor tests (the sample app, the starter's own tests) use `project(":…")` references, so they
exercise the **source**, not the **packaged artifact** — they can't catch packaging regressions: a
starter that drops `ihawu-core` to `implementation` (breaking `@IhawuResource` for consumers), a
malformed or missing POM/coordinate, or auto-configuration that doesn't load from the jar. This build
consumes the **published** artifacts exactly as an external user would, so those regressions fail here
instead of in someone's project.

## Modules

| Module | Consumes | Proves |
| --- | --- | --- |
| `:spring-boot-starter` | `org.ihawu:ihawu-spring-boot-starter` | the published starter auto-configures masking and exposes `ihawu-core` transitively (`api`) |
| `:kmp` _(future)_ | the KMP artifact | the published multiplatform library is consumable |

## What it proves (that the in-reactor sample can't)

- The published starter **auto-configures** masking from the jar (not from project wiring).
- `@IhawuResource` and the core policy types are reachable with **only the starter declared** — i.e.
  `ihawu-core` is transitive via `api`. A regression to `implementation` fails the smoke test's compile.
- The published **POM** resolves correctly (`starter → core` at `compile` scope).

## Run it

```bash
# 1. publish core + starter to your local Maven repo
./gradlew publishToMavenLocal

# 2. run the smoke tests (separate build; resolves org.ihawu:* from mavenLocal)
./gradlew -p smoke-test test                      # all adapters
./gradlew -p smoke-test :spring-boot-starter:test # one adapter
```

Override the consumed version with `-PihawuVersion=<version>` (defaults to `0.1.0-SNAPSHOT`, set in
`gradle.properties`).

## In CI

`verify.yml` runs `publishToMavenLocal` then `./gradlew -p smoke-test test` on every PR and push to
`main` (skipped on release tags), gating every change on "the published shape is consumable".
