# Security Policy

## Supported Versions

Ihawu is pre-1.0. Only the most recent release receives security fixes — there are no backports to earlier
0.x versions. Upgrade to the latest release before reporting.

| Version         | Supported          |
|-----------------|--------------------|
| Latest 0.x      | :white_check_mark: |
| Earlier 0.x     | :x:                |

## Reporting a Vulnerability

**Do not open a public GitHub issue for a security vulnerability.**

Report it through **[GitHub Private Vulnerability Reporting](https://github.com/maureenCindy/ihawu/security/advisories/new)** —
the *Security* tab on this repository, then *Report a vulnerability*. The report stays private between you and
the maintainers until a fix is published.

Please include a description of the vulnerability, steps to reproduce it, the impact you believe it has, and a
suggested fix if you have one.

Ihawu is maintained by one person. You will get an acknowledgement as soon as I can manage it, and I will keep
you updated as I work on it — but I would rather set an honest expectation than a service level I might miss
during a live disclosure.

## What Counts as a Vulnerability

Ihawu enforces masking at **one** exit: an `ObjectMapper` with `IhawuModule` registered, serializing a call
that has an `IhawuPrincipal` attached. That boundary defines the scope of this policy.

**In scope** — please report these:

- A field a policy marks `HIDE` or `REDACT` appears **unmasked** in a serialized response.
- `REDACT` emits the **original value** rather than the placeholder.
- A resource serializes **unmasked when no principal is attached** — Ihawu is meant to fail closed and emit `{}`.
- One caller's resolved policies are applied to **another caller** (cross-request contamination).
- A caller obtains a **less restrictive** policy set than the resolver returned for them.

**Out of scope** — these are documented behaviour, not vulnerabilities:

- **Sensitive data reaching a sink Ihawu does not cover.** Ihawu does not mask values written to logs, published
  to Kafka, written to a cache, exported to CSV, rendered into a server-side template, or serialized by a second
  `ObjectMapper` without the module registered. This is inherent to enforcing at the serialization boundary and is
  documented in full:
  [What Ihawu does not protect](https://ihawu.org/concepts/how-it-works/#what-ihawu-does-not-protect).
- **Unconfigured fields serializing normally.** Masking is a denylist: a field no policy restricts is public by
  design. See [How Ihawu Works](https://ihawu.org/concepts/how-it-works/).
- **An empty `{}` response after a policy-resolution failure.** That is the intended fail-closed behaviour.
- **An application that fails to start on an unenforceable masking policy.** A policy that cannot satisfy
  its field's declared type (`REDACT` on a non-nullable non-`String`, `HIDE` on a non-nullable field, or a
  field the resource does not have) fails the context at startup by design — a config error surfaced early,
  not a vulnerability ([ADR 0005](docs/adr/0005-hard-fail-on-unenforceable-masking-policy.md)).

## Disclosure Policy

- Coordinated disclosure. Please allow a reasonable window — typically 90 days — to address the issue before
  disclosing publicly.
- Credit is given to reporters in the release notes and the published advisory, unless you ask to remain
  anonymous.
