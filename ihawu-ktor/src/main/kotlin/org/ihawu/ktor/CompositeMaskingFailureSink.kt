package org.ihawu.ktor

import org.ihawu.core.masking.MaskingFailure
import org.ihawu.core.masking.MaskingFailureSink

/**
 * Fans a fail-closed notification out to several [MaskingFailureSink]s in order — e.g. a logger *and*
 * [MicrometerMaskingFailureSink], so adding metrics never costs the log line:
 *
 * ```
 * onFailClosed = CompositeMaskingFailureSink(myLoggingSink, MicrometerMaskingFailureSink(registry))
 * ```
 */
public class CompositeMaskingFailureSink(
    private vararg val sinks: MaskingFailureSink,
) : MaskingFailureSink {
    override fun onFailClosed(failure: MaskingFailure) {
        sinks.forEach { it.onFailClosed(failure) }
    }
}
