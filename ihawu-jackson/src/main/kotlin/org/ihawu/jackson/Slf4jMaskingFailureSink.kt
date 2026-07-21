package org.ihawu.jackson

import org.ihawu.core.masking.FailReason
import org.ihawu.core.masking.MaskingFailure
import org.ihawu.core.masking.MaskingFailureSink
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The JVM [MaskingFailureSink]: logs each fail-closed drop via SLF4J with the resource (and field) name
 * only — never the protected value. Resource-level failures ([FailReason.NO_PRINCIPAL],
 * [FailReason.RESOLVER_ERROR]) log once per (call, resource); per-field failures log per field.
 *
 * Keeping logging here rather than in the engine is what leaves `ihawu-core`'s masking model free of a
 * logging dependency.
 */
public class Slf4jMaskingFailureSink : MaskingFailureSink {
    override fun onFailClosed(failure: MaskingFailure) {
        when (failure.reason) {
            FailReason.NO_PRINCIPAL ->
                log.warn(
                    "No IhawuPrincipal attached to serialization call, serialization failing closed for resource '{}'. " +
                        "Attach one via ObjectWriter.withAttribute(IhawuSerialization.PRINCIPAL, principal).",
                    failure.resource,
                )
            FailReason.RESOLVER_ERROR ->
                log.error("Ihawu masking failed for resource '{}', serialization failing closed", failure.resource, failure.cause)
            FailReason.HIDE_NON_NULLABLE ->
                log.error("Ihawu HIDE on required field '{}.{}'; omitting (fail-closed)", failure.resource, failure.field)
            FailReason.REDACT_UNSAFE ->
                log.error("Ihawu REDACT on non-nullable non-String '{}.{}'; omitting (fail-closed)", failure.resource, failure.field)
        }
    }

    private companion object {
        val log: Logger = LoggerFactory.getLogger(Slf4jMaskingFailureSink::class.java)
    }
}
