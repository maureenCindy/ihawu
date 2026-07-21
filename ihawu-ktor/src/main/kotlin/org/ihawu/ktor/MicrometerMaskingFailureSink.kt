package org.ihawu.ktor

import io.micrometer.core.instrument.MeterRegistry
import org.ihawu.core.masking.FailReason
import org.ihawu.core.masking.MaskingFailure
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
    override fun onFailClosed(failure: MaskingFailure) {
        registry.counter("ihawu.masking.failures", "resource", failure.resource, "reason", failure.reason.name).increment()
    }
}
