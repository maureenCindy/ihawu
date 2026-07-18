package org.ihawu.core.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter
import org.ihawu.core.masking.MaskingCapability
import org.ihawu.core.masking.MaskingContext
import org.ihawu.core.masking.MaskingDecision
import org.ihawu.core.masking.MaskingEngine
import org.ihawu.core.policy.IhawuPrincipal

/**
 * A Jackson [BeanPropertyWriter] that enforces one field's masking decision at serialization time.
 *
 * It is the *write* half of the split: the serialization-neutral [MaskingEngine] decides what to do
 * with the field (policy resolution, per-call memoization, fail-closed behaviour, the type contract);
 * this writer only executes the resulting [MaskingDecision] against Jackson —
 * [MaskingDecision.Pass] delegates to normal output, [MaskingDecision.Omit] drops the field,
 * [MaskingDecision.WriteString]/[MaskingDecision.WriteNull] write the masked value. Fail-closed
 * diagnostics are surfaced by the engine through its failure sink, not here.
 *
 * The per-call context (principal + policy memoization) rides on the [SerializerProvider]'s attributes
 * via [JacksonMaskingContext], scoping it to a single write call. See
 * `docs/adr/0001-serialization-context-passing.md` and ADR 0006.
 *
 * @param base The original writer this delegates to for unmasked output.
 * @property engine Decides each field's [MaskingDecision].
 * @property resource The [org.ihawu.core.annotation.IhawuResource] name whose policies apply.
 * @property capability How this field may be masked, derived once per type from its declared type.
 */
internal class MaskingPropertyWriter(
    base: BeanPropertyWriter,
    private val engine: MaskingEngine,
    private val resource: String,
    private val capability: MaskingCapability,
) : BeanPropertyWriter(base) {
    override fun serializeAsField(
        bean: Any,
        gen: JsonGenerator,
        prov: SerializerProvider,
    ) {
        when (val decision = engine.decide(resource, name, capability, JacksonMaskingContext(prov))) {
            MaskingDecision.Pass -> super.serializeAsField(bean, gen, prov) // not in policy -> normal output
            is MaskingDecision.Omit -> Unit // field omitted (fail-closed reasons are surfaced by the engine)
            is MaskingDecision.WriteString -> {
                gen.writeFieldName(name)
                gen.writeString(decision.value)
            }
            MaskingDecision.WriteNull -> {
                gen.writeFieldName(name)
                gen.writeNull()
            }
        }
    }
}

/** Adapts a Jackson [SerializerProvider] to the neutral [MaskingContext] for one write call. */
private class JacksonMaskingContext(
    private val prov: SerializerProvider,
) : MaskingContext {
    override val principal: IhawuPrincipal?
        get() = prov.getAttribute(IhawuSerialization.PRINCIPAL) as? IhawuPrincipal

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> memoize(
        key: String,
        compute: () -> T,
    ): T {
        (prov.getAttribute(key) as? T)?.let { return it }
        return compute().also { prov.setAttribute(key, it) }
    }
}
