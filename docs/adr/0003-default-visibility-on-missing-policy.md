# ADR 0003 — Default visibility when no policy matches

- **Status:** Accepted
- **Date:** 2026-06-30
- **Relates to:** #21 (`RoleBasedResourcePolicyResolver`), #26 (principal mapping), #27 (interceptor registration)
- **Depends on:** #17 (`ResourcePolicyResolver`, `IhawuPrincipal`)
- **Builds on:** [ADR 0001](0001-serialization-context-passing.md)

## Context

When Ihawu serializes an `@IhawuResource`, it asks the resolver for the caller's
`List<FieldPolicy>` for that resource and masks accordingly. But "nothing matches" can arise in two
structurally different ways, and they must not be conflated:

1. **Missing identity** — there is no authenticated principal on the call (unauthenticated request,
   or Spring Security absent). Handled in the serializer (`MaskingPropertyWriter`): no principal →
   `MASK_ALL` → the resource serializes as `{}`.
2. **Missing policy** — a principal *is* present, but the resolver returns an **empty**
   `List<FieldPolicy>` for this `(resource, role)` (e.g. a `RoleBasedResourcePolicyResolver` whose
   rules don't mention this role, or mention the resource but not for any role the principal holds).

Case 1 is settled (ADR 0001 / the fail-closed work in #23): no verified identity ⇒ leak nothing.
This ADR decides **case 2**:

> When an authenticated principal resolves to *no* field policies for a resource, what should the
> response contain — the unmasked data, or nothing (`{}`)?

The question is security-adjacent and easy to get backwards, so it is judged by its failure modes
and by which architectural layer rightfully owns the decision.

## Options considered

### Option A — Fail open on missing policy (chosen)

An empty policy list means "no fields are restricted here," so the resource serializes **unmasked**.
Fail-closed is reserved for **missing identity** (case 1), which already yields `{}`.

### Option B — Fail closed on missing policy

Treat an empty policy list as "deny": mask every field (or emit `{}`) whenever the resolver returns
no rules for the `(resource, role)`.

### Option C — A configurable global default in core

A core-level switch (`failClosed = true/false`) that flips the meaning of an empty policy list
application-wide.

## Analysis

### What kind of tool Ihawu is (decisive)

- **Masking is *subtractive*, a denylist.** Ihawu's job is to *hide specific fields*. A field no
  policy mentions is public *by design*. "No rules for this role" therefore means "nothing here is
  restricted for them," not "deny everything." Option B inverts this into an **allowlist**: you
  would have to author an explicit policy for every `(resource × role)` pair just to see *any* data.
  For the overwhelming majority of resources/fields — which are not sensitive — that is unusable.
- **Ihawu is a Policy *Enforcement* Point, not a Policy *Decision* Point, and not an authorizer.**
  It masks fields *within a resource the caller is already authorized to receive*. "Should this user
  receive this endpoint at all?" is an authentication/authorization decision made **upstream** (the
  web framework, Spring Security, OPA, the app's PDP) and enforced *before* serialization. If Ihawu
  returned `{}` for an unrecognized role, it would be silently performing access control it does not
  own — and disguising "you weren't authorized" as "the data was masked," which is misleading and
  splits the authorization story across two layers.

### Correctness of the resolver contract

- `RoleBasedResourcePolicyResolver` **unions** policies across all of the principal's roles. A
  principal with `[ADMIN, TEMP]`, where `TEMP` is unconfigured, must still receive ADMIN's masking.
  Under Option B, any single unconfigured role would force the whole resource to `{}`, collapsing
  multi-role users to nothing — plainly wrong.
- An empty list is a **valid, explicit decision** — "mask nothing" — not an error or an unknown.
  Errors (the resolver *throwing*) are a separate concern and already fail closed (#23). Folding
  "successfully resolved to no restrictions" into "deny" erases that distinction.

### Where fail-closed belongs

- The security-critical guarantee is **"no verified identity ⇒ reveal nothing."** That lives at the
  *identity* boundary and is already fail-closed (case 1 → `{}`). Once identity is established, the
  safe **and usable** default for fields the app author chose not to restrict is "visible." Pushing
  fail-closed down to the *policy-content* boundary as well does not add security — an authenticated
  caller seeing non-restricted fields is the intended behavior — it only breaks usability.

### Why not bake a toggle into core (Option C)

- Deny-by-default is a legitimate posture for some threat models — but it is a **policy decision**,
  and the right home for policy is the resolver/PDP, not the masking engine. A core switch makes
  `ihawu-core` opinionated about authorization semantics, adds a config surface whose wrong setting
  is a security bug, and still wouldn't express richer rules ("deny role X, allow role Y by
  default"). Keeping core neutral lets each org choose its posture *in the layer that owns policy*.

## Decision

**Adopt Option A. Ihawu fails closed on missing *identity* and fails open on missing *policy*.**

- No principal on the call → `{}` (unchanged; ADR 0001 / #23).
- A principal with an **empty** resolved `List<FieldPolicy>` for a resource → the resource
  serializes **unmasked**. `ihawu-core` enforces exactly what the resolver returns and stays neutral
  on the default; the resolver "decides default visibility."

Rule of thumb: **fail closed on missing identity; fail open on missing policy.**

## Consequences

### The intended asymmetry (and how to test it)

| Situation | Layer | Result |
| --- | --- | --- |
| No authenticated principal | serializer (`MaskingPropertyWriter`) | `{}` — **fail closed** |
| Principal present, resolver throws | serializer (#23) | `{}` — **fail closed** |
| Principal present, resolver returns empty policy | resolver contract | full object — **fail open** |
| Principal present, resolver returns HIDE/REDACT rules | serializer | masked fields |

These are pinned end-to-end in the starter's `IhawuResourceMaskingTest`: an ADMIN sees masked
fields, a USER with no configured rules sees the whole object (fail open), and an anonymous request
to a permit-all route gets `{}` (fail closed).

### If you want deny-by-default

It is achievable **without** changing the masking engine — push it into the layer that owns policy:

- author a catch-all rule in your configuration, or
- provide a custom `ResourcePolicyResolver` that returns a HIDE-everything policy for unconfigured
  `(resource, role)` pairs, or
- block the request upstream (Spring Security / PDP) so it never reaches serialization.

### Risk acknowledged

The cost of fail-open-on-missing-policy is that a *forgotten* policy leaves a sensitive field
exposed rather than safe. We accept this because the alternative (deny-by-default in core) makes the
tool unusable for its primary purpose and usurps authorization; the mitigation is that sensitivity
is opt-in per field and deny-by-default remains one custom resolver away for orgs that need it.

## Alternatives rejected

- **Option B (fail closed on missing policy)** — rejected: turns a denylist masking tool into an
  allowlist requiring exhaustive `(resource × role)` policies to see any data; breaks multi-role
  union; and performs whole-resource authorization that Ihawu does not own.
- **Option C (core `failClosed` toggle)** — rejected: makes `ihawu-core` opinionated about
  authorization, adds a security-sensitive config surface, and still can't express per-role
  defaults; policy posture belongs in the resolver/PDP, not the engine.
