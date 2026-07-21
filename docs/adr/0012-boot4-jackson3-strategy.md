# ADR 0012 — Spring Boot 4 / Jackson 3 strategy for 1.0

- **Status:** Accepted
- **Date:** 2026-07-21
- **Relates to:** #121 (road to 1.0); Dependabot #41
- **Depends on:** [ADR 0006](0006-serialization-neutral-masking-spi.md) (serialization-neutral SPI)

## Context

1.0 freezes the public API (#118–#120), so the dependency baseline it ships on must be a decision, not an
accident. The facts as of July 2026:

- **Spring Boot 3.5 reached OSS end-of-life on 2026-06-30**; 3.5.16 — the exact version the starter builds
  against — was the final OSS patch. All 3.x branches are OSS-unsupported; commercial support continues.
- **Spring Boot 4 mandates Jackson 3**, which moved to new Maven coordinates and packages
  (`tools.jackson.*`). Boot 4 ships a *deprecated* `spring-boot-jackson2` stop-gap.
- **Jackson 2 is not EOL** — `jackson-databind` 2.x remains current and maintained. The EOL concern is
  confined to the starter's Boot pin, not the `ihawu-jackson` backend.
- **Jackson 2 and Jackson 3 can coexist in the same JVM** (distinct coordinates and packages), so a
  Jackson 3 backend is cleanly *additive* — precisely the multiplicity the serialization-neutral core
  (ADR 0006) was built to allow.

Waiting for a Boot 4 port before 1.0 would couple the API freeze — which is finished, mechanically
enforced (BCV), and independent of any backend — to a new backend build-out.

## Decision

**1.0 ships on the current stack; Boot 4 / Jackson 3 land as additive artifacts in 1.1.**

1. **1.0 baseline:** `ihawu-jackson` on Jackson 2.x (alive, supported); `ihawu-spring-boot-starter` on
   Spring Boot 3.5.x — with the compatibility policy (#122) stating plainly that Boot 3.5 is OSS-EOL and
   suited to commercially-supported 3.5 estates; `ihawu-kotlinx`/`ihawu-ktor` are unaffected by any of
   this.
2. **1.1 (fast-follow, additive — no major needed):**
   - **`ihawu-jackson3`** — the masking backend on `tools.jackson.*`. A port of `ihawu-jackson` against
     Jackson 3's serializer SPI, implementing the same neutral `MaskingEngine` contract; coexistence means
     both backends can even share a JVM.
   - **A Boot 4 starter** (working name `ihawu-spring-boot4-starter`, final name decided at build time) —
     depends on `ihawu-jackson3` (Boot 4's `JsonMapper` is Jackson 3; a Jackson 2 module cannot plug into
     it). The existing starter continues serving Boot 3.5 users unchanged.
   - Sequencing is forced: `ihawu-jackson3` first, the Boot 4 starter on top.
3. **What would constitute a 2.0:** *removing* the Jackson 2 backend or the Boot 3.5 starter, or a change
   to the frozen core SPI. Merely *adding* the Jackson 3/Boot 4 artifacts is 1.x by definition. No removal
   is planned while Jackson 2 remains maintained.
4. **Dependabot #41** (starter Boot 3.5.16 → 4.1.0) is **declined and closed**: the starter's 1.0 line
   stays on Boot 3.5 by this ADR; Boot 4 arrives as a separate artifact, not an in-place bump. (#64,
   vanniktech publish plugin, is unrelated build tooling — routine maintenance, out of this ADR's scope.)

## Why not wait for Boot 4 before 1.0

- The freeze is about the **core SPI and adapter contracts**, all finished and backend-agnostic; the Boot 4
  work is *additive by construction*, so nothing about it benefits from happening pre-freeze.
- A near-term 1.0 keeps the release train paced (Maven Central publishing limits) and the grant-visible
  milestone crisp.
- The honest cost — 1.0's starter targets an OSS-EOL Boot line — is mitigated by stating it in the
  compatibility policy and by the 1.1 fast-follow, rather than by delaying the freeze.

## Consequences

| Concern | Outcome |
| --- | --- |
| 1.0 timing | Uncoupled from Boot 4; proceeds on the frozen surface. |
| Boot 3.5 OSS-EOL | Disclosed in the compatibility policy (#122); existing starter keeps working for commercial-3.5 estates. |
| Jackson 3 | `ihawu-jackson3` in 1.1 — additive, coexists with Jackson 2 in one JVM. |
| Boot 4 | New starter artifact in 1.1, on top of `ihawu-jackson3`; forced sequencing recorded. |
| Semver | Additions are 1.x; removals of the Jackson 2/Boot 3.5 artifacts (none planned) would be 2.0. |
| SPI validation | A fourth backend against the unchanged `MaskingEngine` further proves ADR 0006's seam. |
