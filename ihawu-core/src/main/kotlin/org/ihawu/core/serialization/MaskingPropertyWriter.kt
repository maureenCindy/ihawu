package org.ihawu.core.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A [BeanPropertyWriter] that enforces a single field's [FieldPolicy] at serialization time.
 *
 * For each field of an [org.ihawu.core.annotation.IhawuResource] bean, the resolved policy decides
 * the output:
 * - no policy — the field serializes normally;
 * - [MaskingStrategy.HIDE] — the field is omitted entirely;
 * - [MaskingStrategy.REDACT] — the value is replaced with [FieldPolicy.placeholder], falling back to
 *   [MaskingStrategy.defaultValue]. Note this writes a string placeholder even for non-string fields.
 *
 * Policies are resolved once per (serialization call, resource) and memoized in the call-scoped
 * [SerializerProvider] attributes, so a response containing many instances of the same resource
 * resolves only once. When no [IhawuPrincipal] is present the writer fails closed: every field is
 * omitted, so the resource serializes as an empty object rather than leaking unmasked data. See
 * `docs/adr/0001-serialization-context-passing.md`.
 *
 * The writer also fails closed on *errors*, never leaking the protected payload:
 * - if [resolver] throws (a misconfiguration or policy-store outage), the failure is memoized and
 *   every field of the resource is omitted, so it serializes as an empty object. This holds for
 *   nested resources and collection items, since each resource resolves independently;
 * - if computing a [MaskingStrategy.REDACT] placeholder throws, that single field is omitted.
 *
 * Both failures are logged for diagnosis with the resource (and field) name only — never the
 * protected value.
 *
 * @param base The original writer this delegates to for unmasked output.
 * @property resolver Resolves the field policies for the [resource].
 * @property resource The [org.ihawu.core.annotation.IhawuResource] name whose policies apply.
 */
internal class MaskingPropertyWriter(
    base: BeanPropertyWriter,
    private val resolver: ResourcePolicyResolver,
    private val resource: String,
) : BeanPropertyWriter(base) {
    override fun serializeAsField(
        bean: Any,
        gen: JsonGenerator,
        prov: SerializerProvider,
    ) {
        val policies = policies(prov) ?: return // fail-closed: omit field -> resource becomes {}
        val policy = policies[name]
        when (policy?.strategy) {
            null -> super.serializeAsField(bean, gen, prov) // not in policy -> normal output
            MaskingStrategy.HIDE -> Unit // omit entirely
            MaskingStrategy.REDACT -> {
                val value =
                    try {
                        policy.placeholder ?: policy.strategy.defaultValue?.invoke() ?: ""
                    } catch (e: Exception) {
                        log.error("Ihawu redaction failed for '{}.{}'; omitting field", resource, name, e)
                        return // fail-closed: omit field
                    }
                gen.writeFieldName(name)
                gen.writeString(value)
            }
        }
    }

    /** Resolve once per (call, resource), memoized in the call-scoped attributes. null => mask everything. */
    private fun policies(prov: SerializerProvider): Map<String, FieldPolicy>? {
        val key = "org.ihawu.resolved:$resource"
        prov.getAttribute(key)?.let { return if (it === MASK_ALL) null else asMap(it) }

        val principal = prov.getAttribute(IhawuSerialization.PRINCIPAL) as? IhawuPrincipal
        if (principal == null) {
            log.warn(
                "No IhawuPrincipal attached to serialization call, serialization failing closed for resource '{}'. " +
                    "Attach one via ObjectWriter.withAttribute(IhawuSerialization.PRINCIPAL, principal).",
                resource,
            )
            prov.setAttribute(key, MASK_ALL)
            return null
        }
        val map =
            try {
                resolver.resolve(principal, resource).associateBy { it.field }
            } catch (ex: Exception) {
                log.error("Ihawu masking failed for resource '{}', serialization failing closed", resource, ex)
                prov.setAttribute(key, MASK_ALL)
                return null
            }
        prov.setAttribute(key, map)
        return map
    }

    @Suppress("UNCHECKED_CAST")
    private fun asMap(value: Any): Map<String, FieldPolicy> = value as Map<String, FieldPolicy>

    private companion object {
        /** Sentinel memoized when no principal is present, so we don't re-check per property. */
        val MASK_ALL = Any()
        val log: Logger = LoggerFactory.getLogger(MaskingPropertyWriter::class.java)
    }
}
