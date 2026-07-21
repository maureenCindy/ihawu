package org.ihawu.core.masking

import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.ResourcePolicyResolver

/**
 * The default [MaskingEngine]: resolves each resource's policy once per call (via
 * [MaskingContext.memoize]) and fails closed, entirely independent of any serialization library.
 *
 * Fail-closed posture (see `docs/adr/0001-serialization-context-passing.md` and ADR 0006):
 * - no principal on the call → the whole resource is masked ([FailReason.NO_PRINCIPAL]);
 * - the [resolver] throws → the whole resource is masked ([FailReason.RESOLVER_ERROR]), unless
 *   [resolverErrorMode] is [ResolverErrorMode.FAIL_REQUEST], which throws [MaskingResolverException] so
 *   the request fails instead of silently returning `{}` (ADR 0011);
 * - a field's policy cannot satisfy the declared type → that field is dropped
 *   ([FailReason.HIDE_NON_NULLABLE] / [FailReason.REDACT_UNSAFE]).
 *
 * Each fail-closed drop notifies [onFailClosed]; resource-level failures fire once per (call, resource)
 * because [MaskingContext.memoize] runs resolution only once.
 *
 * @property resolver Resolves the field policies for a `(principal, resource)`.
 * @property onFailClosed Notified on each fail-closed drop; defaults to a no-op so core needs no logger.
 * @property resolverErrorMode How a resolver failure is handled; defaults to [ResolverErrorMode.MASK_ALL].
 */
public class DefaultMaskingEngine(
    private val resolver: ResourcePolicyResolver,
    private val onFailClosed: MaskingFailureSink = MaskingFailureSink { _ -> },
    private val resolverErrorMode: ResolverErrorMode = ResolverErrorMode.MASK_ALL,
) : MaskingEngine {
    override fun decide(
        resource: String,
        field: String,
        capability: MaskingCapability,
        context: MaskingContext,
    ): MaskingDecision =
        when (val resolved = context.memoize(key(resource)) { resolve(resource, context) }) {
            is Resolved.MaskAll -> MaskingDecision.Omit(resolved.reason)
            is Resolved.Ok -> decideField(resource, field, resolved.byField[field], capability)
        }

    /** Resolve once per (call, resource); a [Resolved.MaskAll] result means fail closed for every field. */
    private fun resolve(
        resource: String,
        context: MaskingContext,
    ): Resolved {
        val principal =
            context.principal ?: run {
                onFailClosed.onFailClosed(MaskingFailure(resource = resource, reason = FailReason.NO_PRINCIPAL))
                return Resolved.MaskAll(FailReason.NO_PRINCIPAL)
            }
        return try {
            Resolved.Ok(resolver.resolve(principal, resource).associateBy { it.field })
        } catch (ex: Exception) {
            onFailClosed.onFailClosed(MaskingFailure(resource = resource, reason = FailReason.RESOLVER_ERROR, cause = ex))
            when (resolverErrorMode) {
                ResolverErrorMode.MASK_ALL -> Resolved.MaskAll(FailReason.RESOLVER_ERROR)
                ResolverErrorMode.FAIL_REQUEST -> throw MaskingResolverException(resource, ex)
            }
        }
    }

    private fun decideField(
        resource: String,
        field: String,
        policy: FieldPolicy?,
        capability: MaskingCapability,
    ): MaskingDecision =
        when (policy?.strategy) {
            null -> MaskingDecision.Pass
            MaskingStrategy.HIDE ->
                if (capability.omittable) {
                    MaskingDecision.Omit()
                } else {
                    onFailClosed.onFailClosed(MaskingFailure(resource = resource, reason = FailReason.HIDE_NON_NULLABLE, field = field))
                    MaskingDecision.Omit(FailReason.HIDE_NON_NULLABLE)
                }
            MaskingStrategy.REDACT -> {
                val placeholder = policy.placeholder ?: policy.strategy.defaultPlaceholder ?: ""
                capability.redactDecision(placeholder).also {
                    if (it is MaskingDecision.Omit && it.reason == FailReason.REDACT_UNSAFE) {
                        onFailClosed.onFailClosed(MaskingFailure(resource = resource, reason = FailReason.REDACT_UNSAFE, field = field))
                    }
                }
            }
        }

    /** The resolution outcome memoized per (call, resource). */
    private sealed interface Resolved {
        data class Ok(
            val byField: Map<String, FieldPolicy>,
        ) : Resolved

        data class MaskAll(
            val reason: FailReason,
        ) : Resolved
    }

    private companion object {
        fun key(resource: String) = "org.ihawu.resolved:$resource"
    }
}
