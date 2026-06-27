package com.ihawu.core.serialization

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.module.SimpleModule
import com.ihawu.core.policy.ResourcePolicyResolver

/**
 * Jackson module that activates Ihawu masking on an `ObjectMapper`.
 *
 * Registering this module installs a serializer modifier that intercepts every
 * [com.ihawu.core.annotation.IhawuResource]-annotated type and applies the
 * [com.ihawu.core.policy.FieldPolicy] list resolved by [resolver] for the call's
 * [com.ihawu.core.policy.IhawuPrincipal]. Types without the annotation serialize unchanged.
 *
 * Ihawu stays a Policy Enforcement Point: it enforces the decisions [resolver] returns and never
 * evaluates policy conditions itself.
 *
 * @property resolver Resolves the field policies that apply to a resource for a given principal.
 * @sample com.ihawu.samples.serialization.maskEmployeeProfile
 * @sample com.ihawu.samples.serialization.maskCollectionItems
 * @sample com.ihawu.samples.serialization.maskFailsClosedWithoutPrincipal
 * @sample com.ihawu.samples.serialization.failClosedOnResolverError
 */
class IhawuModule(
    private val resolver: ResourcePolicyResolver,
) : SimpleModule("IhawuModule") {
    override fun setupModule(context: Module.SetupContext) {
        super.setupModule(context)
        context.addBeanSerializerModifier(IhawuBeanSerializerModifier(resolver))
    }
}
