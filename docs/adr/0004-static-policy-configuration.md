# ADR 0004 — Supplying static masking policy from configuration

- **Status:** Accepted
- **Date:** 2026-06-30
- **Relates to:** #46 (bind static `ResourcePolicy` rules from `ihawu.*`)
- **Depends on:** #24 (`IhawuProperties`), `ResourcePolicyProvider` SPI, #21 (`RoleBasedResourcePolicyResolver`)
- **Builds on:** [ADR 0003](0003-default-visibility-on-missing-policy.md) (default visibility on missing policy)

## Context

`ResourcePolicyProvider` is the starter's seam for supplying the static `List<ResourcePolicy>` that
drives masking. Its default implementation returns `emptyList()` — so today, masking rules can only
be supplied by an application **writing code** (a custom `ResourcePolicyProvider` or
`ResourcePolicyResolver` bean). For a simple app with a fixed, well-known set of rules, that is more
ceremony than the task warrants.

We want a **zero-code path**: declare static rules in `application.yml` under `ihawu.*`, and have the
starter assemble the resolver from them. The provider SPI stays the escape hatch for **dynamic**
sources (a database, OPA, a per-tenant service). The open question this ADR settles is *how* config
is bound — specifically, **what types sit on the configuration contract** — and what guarantees the
binding must give.

The core domain types are:

```kotlin
// ihawu-core
data class ResourcePolicy(val resourceName: String, val roleFieldPolicies: Map<String, List<FieldPolicy>>? = null)
data class FieldPolicy(val field: String, val strategy: MaskingStrategy, val placeholder: String? = null)
enum class MaskingStrategy { HIDE, REDACT }
```

Target shape in `application.yml`:

```yaml
ihawu:
  policies:
    - resource: employee
      roles:
        ADMIN:
          - { field: salary, strategy: HIDE }
          - { field: ssn, strategy: REDACT, placeholder: "***ssn" }
```

## Options considered

### Option A — Starter-local config DTOs, mapped to core in the default provider (chosen)

Introduce binder DTOs in the starter (e.g. `IhawuProperties.PolicyProperties` /
`FieldPolicyProperties`) bound from `ihawu.policies.*`. The **default** `ResourcePolicyProvider`
reads `IhawuProperties` and maps the DTOs to core `ResourcePolicy`. Core types never touch Spring's
binder.

### Option B — Bind core `ResourcePolicy`/`FieldPolicy` directly onto `IhawuProperties`

`var policies: List<ResourcePolicy>` on `IhawuProperties`, bound straight into the core types.

### Option C — No static config; keep only the programmatic SPI (status quo)

Leave the `emptyList()` default; every app that wants rules writes a provider bean.

### Option D — External policy-file reference (`ihawu.policy-location: classpath:…`)

Point at a separate YAML/JSON document parsed at startup.

## Analysis

### Core must not sit on the configuration contract (decisive)

`ihawu-core` is deliberately framework-agnostic — it has no Spring dependency (see the module
split). Option B breaks that boundary *by reference*: the moment `ResourcePolicy` and `FieldPolicy`
are bound from `ihawu.*`, their field names and shapes become a **public configuration API**. A
later, perfectly reasonable core refactor (rename `resourceName`, restructure `roleFieldPolicies`,
split `FieldPolicy`) silently becomes a **breaking change to users' `application.yml`** — a coupling
the module split exists to prevent. Keeping a starter-local DTO between config and core lets the two
evolve independently: the config surface is owned by the starter, the domain model by core.

### The core shapes are not ideal config DTOs anyway

`roleFieldPolicies: Map<String, List<FieldPolicy>>? = null` is nullable for the *resolver's* benefit
(ADR 0003: a `null`/empty map means "no role rules ⇒ fail open"), not the operator's. Exposing that
nullability in YAML invites confusing, under-specified config. A DTO can present a non-null,
operator-friendly shape (`roles: Map<String, List<…>> = emptyMap()`) and translate to the core
contract during mapping. `MaskingStrategy` binds cleanly by enum name (`HIDE`/`REDACT`) via relaxed
binding either way.

### Config is a *default*, not a new override axis

The provider SPI is already the override point and is `@ConditionalOnMissingBean`. Config-supplied
rules must slot in **underneath** that: the default provider reads `IhawuProperties`, and an
application that defines its own `ResourcePolicyProvider` (or whole `ResourcePolicyResolver`) still
wins, unchanged. This keeps one mental model — *static config for the simple case, a bean for the
dynamic case* — rather than adding a competing source that has to be reconciled with the SPI.

### Silent under-masking is the dangerous failure mode

Static masking config is security-sensitive in an asymmetric way: a typo in `resource:` or a
mis-keyed role does not throw — it just **masks nothing**, leaving a sensitive field exposed while
the app looks healthy (this is the fail-open posture ADR 0003 deliberately chose for *missing
policy*). Binding from free-form YAML widens the surface for exactly that mistake. So the binding
must **fail fast** on the errors it *can* detect mechanically:

- **Duplicate `resource` keys** — two entries for the same resource would otherwise silently collide
  when `RoleBasedResourcePolicyResolver` indexes by name; reject at startup.
- **Empty/blank `field`** — a policy that masks nothing meaningful is almost certainly a mistake.
- Unknown `strategy` already fails at bind time (enum).

And it must make the loaded ruleset **observable**: log a one-line summary at startup (resource
count, per-resource role/field counts) so operators can confirm what was actually loaded. We cannot
detect a *forgotten* rule, but we can refuse a *malformed* one and surface what we have.

### Refresh semantics

Static config is read once; the role-based resolver is a startup singleton built from it. Editing
YAML requires a restart. That is acceptable for static rules and is the dividing line with the SPI:
anything that must change at runtime belongs in a custom resolver, not in `ihawu.*`.

### Why D is out of scope for now

A file reference is genuinely useful once rule sets outgrow inline YAML, but it adds a parser, a
resource-loading/path story, and its own error handling. Inline `ihawu.policies` plus the SPI covers
the v1 need; the file option can layer on later without changing this decision (it would feed the
same DTOs).

## Decision

**Adopt Option A.** Bind static rules from `ihawu.policies.*` into **starter-local config DTOs**, and
map them to core `ResourcePolicy` inside the **default** `ResourcePolicyProvider`. Core types stay
off the configuration contract. A user-supplied `ResourcePolicyProvider`/`ResourcePolicyResolver`
bean still overrides via the existing `@ConditionalOnMissingBean`. Binding **fails fast** on
duplicate resources and empty fields, and logs a one-line summary of the loaded policies.

Rule of thumb: **config is the zero-code default for static rules; the provider SPI is the seam for
dynamic ones — and the core domain model never becomes the YAML schema.**

## Consequences

| Concern | Outcome |
| --- | --- |
| Simple app, fixed rules | Declare `ihawu.policies` in YAML; no beans, no code. |
| Dynamic rules (DB/OPA/per-tenant) | Provide a `ResourcePolicyProvider`/`ResourcePolicyResolver` bean; config default backs off. |
| Core refactor | Free to change `ResourcePolicy`/`FieldPolicy`; only the starter's DTO→core mapping adjusts, never users' YAML. |
| Malformed config | Duplicate resource / empty field → startup failure with a clear message. |
| IDE support | `spring-boot-configuration-processor` (already on the starter) emits metadata, so `ihawu.policies.*` autocompletes. |
| Visibility of loaded rules | One-line startup log summarising the ruleset. |

### Interaction with ADR 0003

Config changes *how rules are supplied*, not *what an empty result means*. A resource absent from
`ihawu.policies`, or present with no rules for the caller's role, still resolves to an empty
`List<FieldPolicy>` and therefore **fails open** (ADR 0003); missing identity still **fails closed**.
The asymmetry is unchanged.

### Out of scope (revisit post-v1)

- External policy-file reference (`ihawu.policy-location`).
- Hot reload / refresh of rules without restart.

## Alternatives rejected

- **Option B (bind core types directly)** — rejected: pins `ihawu-core`'s domain model as a public
  configuration contract, turning core refactors into breaking YAML changes, and exposes
  resolver-oriented nullability that confuses operators.
- **Option C (SPI only)** — rejected: forces every app, however simple, to write a bean for static
  rules; the zero-code path is a meaningful adoption and DX win.
- **Option D (external file)** — deferred, not rejected: useful at scale, but adds loading/parsing
  surface beyond the v1 need; can layer onto Option A later by feeding the same DTOs.