package com.ihawu.spring.boot.starter.configuration

import com.ihawu.core.policy.ResourcePolicy

/**
 * Supplies the static [ResourcePolicy] rules that drive masking.
 *
 * Defaults to an empty list — no policies, so nothing is masked — until rules are supplied. Provide
 * your own `ResourcePolicyProvider` bean to override. Binding the rules from `ihawu.*` configuration
 * is tracked separately (#21); for now they are supplied programmatically.
 */
fun interface ResourcePolicyProvider {
    /** @return the list of [ResourcePolicy] rules to enforce. */
    fun getResourcePolicies(): List<ResourcePolicy>
}
