# ADR 0011 — Configurable `fail-request` on a resolver-error, on the neutral SPI

- **Status:** Accepted
- **Date:** 2026-07-18
- **Relates to:** #89 (observable & configurable fail-closed)
- **Depends on:** [ADR 0006](0006-serialization-neutral-masking-spi.md) (neutral SPI), [ADR 0003](0003-default-visibility-on-missing-policy.md), [ADR 0005](0005-hard-fail-on-unenforceable-masking-policy.md)

## Context

Ihawu is **fail-closed**: if the policy resolver throws — a policy-store outage or misconfiguration —
`DefaultMaskingEngine` masks the whole resource to `{}` and the request still returns **HTTP 200**. That is
safe (nothing leaks) but *silent*: a site-wide outage looks like success to callers and monitoring. #89
makes this **observable** (the `MaskingFailureSink` seam, shipped in 0.3.0) and **configurable** — an
operator can choose to turn a resolver outage into a `5xx` instead.

The masking engine is now serialization-neutral ([ADR 0006](0006-serialization-neutral-masking-spi.md)), so
this decision must live on the engine, not in the Jackson writer — the same choice means different things
at a Jackson/servlet sink, a Kafka sink, or a kotlinx.serialization sink.

## Decision

Add a **`ResolverErrorMode`** to `DefaultMaskingEngine` (commonMain), default **`MASK_ALL`**:

- **`MASK_ALL`** (default, unchanged) — resolver throws → notify `onFailClosed(resource, null,
  RESOLVER_ERROR, cause)` → mask the whole resource. Fail-closed, HTTP 200.
- **`FAIL_REQUEST`** — resolver throws → notify the sink *(same event, so metrics still fire)* → **throw a
  typed `MaskingResolverException(resource, cause)`**. The exception propagates out of serialization; the
  adapter maps it to a `5xx`.

Three sub-decisions:

1. **Scoped to the `RESOLVER_ERROR` path only.** `NO_PRINCIPAL` stays `MASK_ALL` under every mode — a
   missing caller is a normal authorization state (an anonymous request legitimately sees `{}`), not an
   outage. The field-contract drops (`HIDE_NON_NULLABLE` / `REDACT_UNSAFE`) also stay fail-closed: the
   static ones are already caught at startup ([ADR 0005](0005-hard-fail-on-unenforceable-masking-policy.md)),
   and a dynamic one is a per-field data issue, not a store outage.

2. **A typed exception, thrown from the engine.** `MaskingResolverException` (commonMain) carries the
   `resource` and the resolver `cause`, so an adapter can recognise it deterministically rather than
   pattern-matching arbitrary throwables. The engine still calls the sink **before** throwing, so
   `fail-request` is strictly *more* observable than `mask-all`, never less.

3. **The sink is unchanged.** No new listener type — `fail-request` reuses the released
   `MaskingFailureSink` (see #89 reconciliation). Observability and the failure mode are orthogonal: the
   sink always fires; the mode only decides whether the engine then masks or throws.

## The committed-response caveat

`fail-request` is **best-effort**, bounded by streaming serialization. A resolver-error surfaces on the
**first field of a resource** (resolution is memoized once per `(call, resource)`), so:

- a **top-level** resource fails *before* any bytes are written → the adapter produces a clean `5xx`;
- a resource nested **past the point the HTTP response is already committed** (status + headers, or an
  already-flushed buffer) cannot become a clean `5xx` — the stream is truncated instead. This is inherent
  to enforcing at the streaming boundary, not specific to Ihawu.

We **document** this rather than solve it in v1 (pre-resolving every reachable resource before serialization
starts is a larger change). `mask-all` — the default — has no such caveat and stays the recommendation for
callers that must always return a well-formed body.

## Consequences

| Concern | Outcome |
| --- | --- |
| Default behaviour | Unchanged — `MASK_ALL`, fail-closed, HTTP 200. Opt-in only. |
| Observability | Independent of the mode: the sink fires on every fail-closed path (0.3.0), including before a `fail-request` throw. |
| Neutral SPI | The mode lives on `DefaultMaskingEngine`; each adapter decides how a thrown `MaskingResolverException` surfaces. |
| Spring Boot | `ihawu.on-policy-failure: mask-all \| fail-request`; a thrown exception maps to `5xx` when the response is not yet committed. |
| Ktor / other sinks | The engine mode exists for them, but exposing it on those adapters is a follow-up (#89 is core + starter). |
| `fail-request` completeness | Best-effort; the committed-response caveat above is documented, not eliminated. |

### Out of scope (follow-ups)
- Exposing `fail-request` on the Ktor adapter (and a Micrometer-equivalent there).
- Pre-serialization resolution to make `fail-request` clean for deeply-nested resources.
