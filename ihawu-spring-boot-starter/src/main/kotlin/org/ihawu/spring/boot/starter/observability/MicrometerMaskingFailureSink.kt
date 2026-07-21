package org.ihawu.spring.boot.starter.observability

import io.micrometer.core.instrument.MeterRegistry
import org.ihawu.core.masking.FailReason
import org.ihawu.core.masking.MaskingFailure
import org.ihawu.core.masking.MaskingFailureSink

/**
 * A [MaskingFailureSink] that counts fail-closed drops on a Micrometer [MeterRegistry] as
 * `ihawu.masking.failures`, tagged by `resource` and `reason` ([FailReason]) — so a policy-store outage
 * or a contract-violating dynamic policy becomes an alertable metric instead of a silent `{}`. Records
 * only the resource and reason, never the protected value.
 */
internal class MicrometerMaskingFailureSink(
    private val registry: MeterRegistry,
) : MaskingFailureSink {
    override fun onFailClosed(failure: MaskingFailure) {
        registry.counter("ihawu.masking.failures", "resource", failure.resource, "reason", failure.reason.name).increment()
    }
}
