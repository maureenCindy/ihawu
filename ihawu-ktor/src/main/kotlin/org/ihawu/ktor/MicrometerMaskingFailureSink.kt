package org.ihawu.ktor

import io.micrometer.core.instrument.MeterRegistry
import org.ihawu.core.masking.FailReason
import org.ihawu.core.masking.MaskingFailureSink

/**
 * A [MaskingFailureSink] that counts fail-closed drops on a Micrometer [MeterRegistry] as
 * `ihawu.masking.failures`, tagged by `resource` and `reason` ([FailReason]) — the **same** metric the
 * Spring Boot starter emits, for cross-adapter consistency. Records only the resource and reason, never
 * the protected value.
 *
 * Ktor has no auto-configuration, so wire it explicitly (optionally composed with a logger via
 * [CompositeMaskingFailureSink]):
 *
 * ```
 * install(IhawuKtor) {
 *     onFailClosed = MicrometerMaskingFailureSink(appMicrometerRegistry)
 * }
 * ```
 */
public class MicrometerMaskingFailureSink(
    private val registry: MeterRegistry,
) : MaskingFailureSink {
    override fun onFailClosed(
        resource: String,
        field: String?,
        reason: FailReason,
        cause: Throwable?,
    ) {
        registry.counter("ihawu.masking.failures", "resource", resource, "reason", reason.name).increment()
    }
}
