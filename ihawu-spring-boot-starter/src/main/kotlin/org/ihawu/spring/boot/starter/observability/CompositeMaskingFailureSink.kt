package org.ihawu.spring.boot.starter.observability

import org.ihawu.core.masking.MaskingFailure
import org.ihawu.core.masking.MaskingFailureSink

/**
 * Fans a fail-closed notification out to several [MaskingFailureSink]s in order — e.g. SLF4J logging
 * *and* Micrometer metrics, so adding metrics never costs the log line.
 */
internal class CompositeMaskingFailureSink(
    private vararg val sinks: MaskingFailureSink,
) : MaskingFailureSink {
    override fun onFailClosed(failure: MaskingFailure) {
        sinks.forEach { it.onFailClosed(failure) }
    }
}
