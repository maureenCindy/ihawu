# Package org.ihawu.spring.boot.starter.configuration

How the starter sources masking policies — the **extension point** you implement, the **configuration**
you set, and the **provided default** that maps one to the other. The Spring auto-configuration that
wires these together is internal and intentionally omitted from this reference.

### Extension point

- [ResourcePolicyProvider] — the seam for supplying [org.ihawu.core.policy.ResourcePolicy] rules.
  Define your own bean (e.g. backed by a database or OPA) to replace the default below.

### Configuration

- [IhawuProperties] — the typed `ihawu.*` configuration, including the `ihawu.policies` rules you set
  in `application.yml`. Kept as a starter-local shape so `ihawu-core` types stay off the config
  contract.

### Provided default

- [ConfigResourcePolicyProvider] — the default [ResourcePolicyProvider], mapping the static
  `ihawu.policies` configuration to core rules. Backs off automatically when you supply your own
  provider bean.

# Package org.ihawu.spring.boot.starter.security

How the starter resolves the caller's identity — the **extension point** you implement and the
**provided default**. The per-request capture filter that feeds the identity into serialization is
internal and omitted from this reference.

### Extension point

- [PrincipalResolver] — maps a Spring Security `Authentication` to a framework-neutral
  [org.ihawu.core.policy.IhawuPrincipal]. Provide your own bean to override — e.g. to read OIDC/JWT
  claims.

### Provided default

- [IhawuPrincipalResolver] — the default [PrincipalResolver]: username, `ROLE_`-stripped authorities,
  and `details` attributes; unauthenticated or anonymous requests resolve to `null` (fail closed).