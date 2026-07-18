package org.ihawu.jackson

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.module.SimpleModule
import org.ihawu.core.masking.DefaultMaskingEngine
import org.ihawu.core.masking.MaskingFailureSink
import org.ihawu.core.masking.ResolverErrorMode
import org.ihawu.core.policy.ResourcePolicyResolver

/**
 * Jackson module that activates Ihawu masking on an `ObjectMapper`.
 *
 * Registering this module installs a serializer modifier that intercepts every
 * [org.ihawu.core.annotation.IhawuResource]-annotated type and applies the
 * [org.ihawu.core.policy.FieldPolicy] list resolved by [resolver] for the call's
 * [org.ihawu.core.policy.IhawuPrincipal]. Types without the annotation serialize unchanged.
 *
 * Ihawu stays a Policy Enforcement Point: it enforces the decisions [resolver] returns and never
 * evaluates policy conditions itself. The masking decisions run through a serialization-neutral
 * [DefaultMaskingEngine]; this module is the Jackson backend that executes them and logs fail-closed
 * drops via SLF4J.
 *
 * @property resolver Resolves the field policies that apply to a resource for a given principal.
 * @property failureSink Notified on each fail-closed drop; defaults to [Slf4jMaskingFailureSink] (logs via
 *   SLF4J). Supply a composite to add metrics without losing the log.
 * @property resolverErrorMode How a resolver failure is handled; defaults to [ResolverErrorMode.MASK_ALL].
 *   [ResolverErrorMode.FAIL_REQUEST] throws so a policy-store outage surfaces as an error (ADR 0011).
 * @sample org.ihawu.samples.serialization.maskEmployeeProfile
 * @sample org.ihawu.samples.serialization.maskCollectionItems
 * @sample org.ihawu.samples.serialization.maskFailsClosedWithoutPrincipal
 * @sample org.ihawu.samples.serialization.failClosedOnResolverError
 */
class IhawuModule(
    private val resolver: ResourcePolicyResolver,
    private val failureSink: MaskingFailureSink = Slf4jMaskingFailureSink(),
    private val resolverErrorMode: ResolverErrorMode = ResolverErrorMode.MASK_ALL,
) : SimpleModule("IhawuModule") {
    override fun setupModule(context: Module.SetupContext) {
        super.setupModule(context)
        val engine = DefaultMaskingEngine(resolver, failureSink, resolverErrorMode)
        context.addBeanSerializerModifier(IhawuBeanSerializerModifier(engine))
    }
}
