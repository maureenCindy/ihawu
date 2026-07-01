package org.ihawu.samples.policy

import kotlin.test.Test

class RoleBasedResourcePolicyResolverSampleTest {
    @Test
    fun `resolvePoliciesForRole sample compiles and runs`() {
        resolvePoliciesForRole()
    }

    @Test
    fun `mostRestrictiveStrategyWinsAcrossRoles sample compiles and runs`() {
        mostRestrictiveStrategyWinsAcrossRoles()
    }

    @Test
    fun `loadResolverFromJson sample compiles and runs`() {
        loadResolverFromJson()
    }
}
