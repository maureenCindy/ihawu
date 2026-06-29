# Architecture Decision Records

This directory records significant architectural decisions for Ihawu — the *why* behind
choices that are hard to reverse or affect security, performance, or the public API.

Each ADR captures the context, the options considered, the decision, and its consequences.
ADRs are immutable once accepted; a later decision that changes course gets a new ADR that
supersedes the old one.

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-serialization-context-passing.md) | How the principal reaches the masking serializer | Accepted |
| [0002](0002-request-scoped-policy-caching.md) | How policy resolution is cached for the request lifecycle | Accepted |
| [0003](0003-default-visibility-on-missing-policy.md) | Default visibility when no policy matches | Accepted |