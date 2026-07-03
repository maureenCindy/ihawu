# Package org.ihawu.core.policy

Policy resolution — split into the **contracts** you integrate against and the **provided
implementations** you can use as-is or compose.

### Contracts & data model

The vocabulary Ihawu speaks regardless of where policies come from. Implement or supply these to
integrate any policy source (config, database, OPA, Casbin, …):

- [ResourcePolicyResolver] — the strategy interface Ihawu calls to resolve a resource's policies for
  a principal. Ihawu enforces what it returns; it never evaluates conditions itself.
- [IhawuPrincipal] — the authenticated identity a resolution is performed for (resolution **input**).
- [FieldPolicy] — a resolved masking decision for one field (resolution **output**).
- [ResourcePolicy] — static `resource → role → field policies` rules consumed by
  [RoleBasedResourcePolicyResolver].

### Provided implementations

Batteries-included [ResourcePolicyResolver]s. Optional — reach for these before writing your own:

- [RoleBasedResourcePolicyResolver] — the default, no-external-dependency resolver driven by static
  [ResourcePolicy] rules (supplied directly or loaded from JSON).
- [CachingResourcePolicyResolver] — a memoizing decorator that resolves each `(principal, resource)`
  at most once per instance lifetime. Wrap any resolver; control cache scope by controlling the
  instance (e.g. one per request).
