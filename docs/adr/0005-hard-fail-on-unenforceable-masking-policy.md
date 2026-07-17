# ADR 0005 — Hard-fail on an unenforceable masking policy

- **Status:** Accepted
- **Date:** 2026-07-17
- **Relates to:** #67 (type-correct masking; merges #68 `HIDE` on non-nullable)
- **Depends on:** #21 (`RoleBasedResourcePolicyResolver`), #46 / [ADR 0004](0004-static-policy-configuration.md) (`ResourcePolicyProvider`, static config)
- **Builds on:** [ADR 0003](0003-default-visibility-on-missing-policy.md) (fail-closed posture), [ADR 0004](0004-static-policy-configuration.md) (fail fast on mechanically-detectable config errors)

## Context

Masking must preserve a resource's **declared type contract** — the masked JSON must still deserialize
into the field's declared type (#67). The design (unified null-default) makes the masked form
type-dependent:

- `String` → a placeholder;
- nullable non-`String` (`Int?`, `Boolean?`, …) → JSON `null`;
- **non-nullable non-`String`** → *no contract-safe masked value exists* — REDACT cannot produce one;
- and `HIDE` may omit a field only where the schema permits absence (nullable/optional).

So some policies are **unenforceable** as written. Two shapes cannot produce contract-valid output:

1. `REDACT` on a non-nullable non-`String` field (e.g. `salary: Int`);
2. `HIDE` on a non-nullable field (e.g. `ssn: String`).

A third masks **nothing**:

3. a policy whose `field` is not a serialized property of the resource (a typo, a renamed field) — it
   silently leaves a sensitive value exposed, the under-masking failure mode ADR 0004 warned about.

For shapes (1) and (2) the core serializer already **fails closed** at runtime — omitting the field
and logging — so no unmasked value ever leaks (consistent with ADR 0003). Shape (3) has **no runtime
net**: an unmatched policy never fires, so the field serializes in the clear. The open question this
ADR settles is what happens *before* runtime, for the **statically-known** policies supplied via
configuration (ADR 0004):

> When a configured policy provably violates the type contract, should the application **refuse to
> start**, or **degrade** and carry on?

This is the same asymmetry ADR 0004 identified for malformed config: a mistake here does not leak, but
it produces a response that silently violates the schema its consumers were generated against. The
type and policy meet at a place we control — startup, where the resource classes and the static rules
are both in hand.

## Options considered

### Option A — Hard-fail the application context at startup (chosen)

Scan for `@IhawuResource` types, cross-check every configured policy against each field's capability,
and if any policy is impossible, throw and fail context refresh with a message naming each
`resource.field` and the remedy.

### Option B — Degrade to a stricter, still-type-correct behaviour with a warning

On an invalid policy, silently substitute the safe behaviour (an invalid `HIDE` becomes `REDACT`; an
invalid `REDACT` becomes field-omission) and log a warning; the app starts.

### Option C — No startup check; rely only on the runtime fail-closed backstop

Do nothing at startup; let the per-field runtime path omit-and-log the first time such a resource is
serialized.

## Analysis

### A misconfigured masking rule is a security-adjacent defect, and honesty beats leniency

Masking is a protection mechanism. A policy that cannot be honoured is a **bug in the security
configuration**, and the least surprising thing a security tool can do with a bug is refuse to run
until it is fixed — not quietly do something *else* that the operator did not ask for. Option B's
"helpful" substitution changes the caller-visible contract (`HIDE`→`REDACT` turns an intended omission
into a present-but-masked value) without anyone deciding that; the warning is easily lost. Option A
turns the mistake into an immediate, unmissable, developer-time failure — the same fail-fast stance
ADR 0004 already took for duplicate resources and blank fields.

### The runtime backstop still exists — startup validation is the *early* line, not the *only* one

Option C is not wrong, just late: the runtime fail-closed path (omit + log) remains regardless, so an
invalid policy never leaks even if it slips past startup (a dynamic resolver, a disabled check). But
"discovered as a fail-closed omission in a downstream consumer's parse error" is a poor first contact
with the problem. Startup validation moves discovery left, to the machine and moment best able to name
the exact `resource.field`.

### Degradation is friendlier to a live policy store — but that is the SPI's job, not config's

Option B's real appeal is a runtime-editable policy store that should not take the app down on a bad
edit. But that is precisely the boundary ADR 0004 drew: **static config is the zero-code default;
dynamic sources are the `ResourcePolicyProvider`/`ResourcePolicyResolver` SPI.** Dynamic rules are not
statically known at startup, so this validator never sees them — they are governed entirely by the
runtime fail-closed backstop, which *is* a graceful degrade (omit + log, no crash). So the two
audiences are already served by two mechanisms; config gets the strict check, the SPI gets the soft
one. We do not need Option B to reconcile them.

### Escape hatch

Hard-fail must be overridable for the rare case where an operator knowingly wants to defer to the
runtime backstop (a migration, a false positive). A single switch, `ihawu.validate-resource-contract`
(default `true`), turns the startup check off; the runtime fail-closed behaviour is unaffected.

### Where the check runs, and the cost

Discovery is a one-time classpath scan of the application's auto-configuration base packages (or
`ihawu.resource-base-packages`) for `@IhawuResource` types. The capability computation is **the same
core code path the serializer uses** (`MaskingCapabilities`), run through the application's
`ObjectMapper`, so the startup verdict matches runtime behaviour exactly — including `@JsonProperty`
renames and any naming strategy. A policy whose **resource** matches no scanned type is skipped (there
is nothing to introspect against); an unknown **field** on a resource that *is* found is flagged, since
it masks nothing and has no runtime net.

## Decision

**Adopt Option A.** At startup, validate every **statically-known** masking policy against its
resource — both that it can produce contract-valid output and that its field actually exists — and
**fail the application context** on any violation, naming each `resource.field` and the fix. The check is on by default and disabled with
`ihawu.validate-resource-contract=false`. **Dynamic** policies (a custom provider/resolver) are not
statically visible and are governed by the runtime **fail-closed** backstop, which omits and logs the
offending field and never leaks. Core owns the *check* and returns violations; the starter owns the
*enforcement* (throwing), keeping `ihawu-core` framework-agnostic and free of the hard-fail policy.

Rule of thumb: **a masking rule that cannot be honoured is a startup failure for static config and a
fail-closed omission for dynamic rules — never a silent contract violation on the wire.**

## Consequences

| Concern | Outcome |
| --- | --- |
| `REDACT` on non-nullable non-`String`, or `HIDE` on non-nullable, in `ihawu.policies` | Context fails at startup, naming `resource.field` and the remedy. |
| A policy `field` that matches no serialized property (typo/rename) | Context fails at startup — caught here because it has no runtime fail-closed net. |
| The same mistake from a dynamic `ResourcePolicyResolver` | Not seen at startup; field is omitted + logged at request time (fail-closed), never leaked. |
| Operator wants to defer to the runtime backstop | `ihawu.validate-resource-contract=false` disables the startup check only. |
| Resources outside the auto-config base packages | Set `ihawu.resource-base-packages`; otherwise those types are not scanned and their policies are not pre-checked. |
| Startup/runtime divergence | None by construction: both use `MaskingCapabilities` through the app's `ObjectMapper`. |
| Core stays framework-agnostic | Core reports violations (`MaskingContractValidator`); only the starter throws. |

### Interaction with ADR 0003 and ADR 0004

ADR 0003's posture is unchanged: missing identity fails closed, missing policy fails open. This ADR
adds a third category — an *impossible* policy — and resolves it by fail-fast at startup (static) or
fail-closed at runtime (dynamic). It extends ADR 0004's "refuse a malformed rule" principle from
*shape* errors (duplicate resource, blank field) to *type-contract* errors.

### Out of scope (revisit later)

- An opt-in **typed-sentinel** escape hatch (redact a non-nullable `Int` to an explicit value like
  `-1`), which would make some currently-invalid policies valid.

## Alternatives rejected

- **Option B (degrade with warning)** — rejected for static config: silently substitutes a different
  caller-visible contract than the operator configured, and the warning is easy to miss. Its valid use
  case (a live policy store) is already served by the SPI's runtime fail-closed path.
- **Option C (runtime-only)** — rejected as the *sole* mechanism: correct but late; it surfaces a
  fixable misconfiguration as a downstream parse error instead of a named startup failure. Retained as
  the backstop *beneath* Option A.