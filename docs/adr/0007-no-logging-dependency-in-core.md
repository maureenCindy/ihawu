# ADR 0007 — No logging dependency in `ihawu-core`; fail-closed observability via the failure-sink SPI

- **Status:** Accepted
- **Date:** 2026-07-18
- **Relates to:** #79 (remove the `slf4j-api` dependency)
- **Builds on:** [ADR 0006](0006-serialization-neutral-masking-spi.md) (`MaskingFailureSink`), #78 (`ihawu-jackson` module)

## Context

`ihawu-core` depended on `org.slf4j:slf4j-api`. SLF4J is JVM-only, so — even after Jackson was removed in
#78 — this alone kept core from compiling to non-JVM targets and blocked the Kotlin Multiplatform work
(#80). Logging cannot simply be deleted, though: it is how the **fail-closed** paths surface at all (no
principal, resolver outage, a policy that can't satisfy the type contract). Removing observability would
turn a silent-`{}` incident into an even quieter one.

#79 listed three options: (1) abstract logging behind a small internal interface in core with a JVM/SLF4J
implementation; (2) fold logging into the failure-listener seam; (3) adopt a KMP-capable logging library.

## Decision

**Adopt option (2) — and it is already realised.** The serialization-neutral SPI work (#77 / ADR 0006)
introduced `MaskingFailureSink`: the engine *notifies* a sink on each fail-closed drop, and core carries
no logger. The JVM logging implementation, `Slf4jMaskingFailureSink`, moved to `ihawu-jackson` in #78. So
#79 reduces to **dropping the now-dead `slf4j-api` declaration from `ihawu-core`** — leaving core with
**no runtime dependencies at all** (`kotlin-stdlib` only), which is the precondition for #80.

Observability is unchanged on the JVM: the starter/Jackson path still wires `Slf4jMaskingFailureSink`, so
the same WARN/ERROR lines are emitted. A non-JVM backend supplies its own `MaskingFailureSink`
implementation (or the no-op default). The richer failure listener + metrics (#89) build on the same seam.

Rule of thumb: **core notifies; a backend decides how to observe.** No logging framework is part of core's
contract.

## Consequences

| Concern | Outcome |
| --- | --- |
| KMP readiness | `ihawu-core` is now `kotlin-stdlib`-only — no JVM-only dependency remains (#80 can proceed). |
| JVM observability | Unchanged — `Slf4jMaskingFailureSink` (in `ihawu-jackson`) emits the same log lines. |
| Non-JVM targets | Provide a `MaskingFailureSink`, or accept the no-op default (fail-closed still occurs, just unobserved). |
| Dependency minimalism | Core keeps its deliberately empty runtime dependency set. |

### Out of scope
- The richer failure-listener API + Micrometer metrics + `fail-request` mode — tracked in #89, built on
  `MaskingFailureSink`.
