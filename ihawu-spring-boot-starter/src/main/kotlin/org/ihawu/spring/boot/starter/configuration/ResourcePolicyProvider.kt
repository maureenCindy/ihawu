package org.ihawu.spring.boot.starter.configuration

import org.ihawu.core.policy.ResourcePolicy

/**
 * Supplies the static [ResourcePolicy] rules that drive masking.
 *
 * Defaults to an empty list — no policies, so nothing is masked — until rules are supplied. Provide
 * your own `ResourcePolicyProvider` bean to override. Binding the rules from `ihawu.*` configuration
 * is tracked separately (#21); for now they are supplied programmatically.
 */
public fun interface ResourcePolicyProvider {
    /** @return the list of [ResourcePolicy] rules to enforce. */
    public fun getResourcePolicies(): List<ResourcePolicy>
}
