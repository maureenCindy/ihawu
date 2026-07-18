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
| [0004](0004-static-policy-configuration.md) | Supplying static masking policy from configuration | Accepted |
| [0005](0005-hard-fail-on-unenforceable-masking-policy.md) | Hard-fail on an unenforceable masking policy | Accepted |
| [0006](0006-serialization-neutral-masking-spi.md) | A serialization-neutral masking SPI | Accepted |
| [0007](0007-no-logging-dependency-in-core.md) | No logging dependency in core; observability via the failure-sink SPI | Accepted |
| [0008](0008-kotlinx-serialization-masking.md) | Masking on kotlinx.serialization (JsonTransformingSerializer + registry, thread-local context) | Accepted |
| [0009](0009-ktor-adapter.md) | The Ktor adapter: a custom ContentConverter + a coroutine-bridge per-call context | Accepted |
| [0010](0010-sealed-polymorphic-kotlinx-masking.md) | Sealed polymorphic `@IhawuResource` masking on kotlinx (supersedes ADR 0008's sealed limitation) | Accepted |
| [0011](0011-configurable-fail-request-on-resolver-error.md) | Configurable `fail-request` on a resolver-error, on the neutral SPI | Accepted |