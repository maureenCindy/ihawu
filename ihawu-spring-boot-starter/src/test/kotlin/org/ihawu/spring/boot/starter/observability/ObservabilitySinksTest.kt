package org.ihawu.spring.boot.starter.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.ihawu.core.masking.FailReason
import org.ihawu.core.masking.MaskingFailureSink
import kotlin.test.Test

class ObservabilitySinksTest {
    @Test
    fun `micrometer sink counts failures tagged by resource and reason`() {
        val registry = SimpleMeterRegistry()
        val sink = MicrometerMaskingFailureSink(registry)

        sink.onFailClosed("employee", null, FailReason.RESOLVER_ERROR, RuntimeException("down"))
        sink.onFailClosed("employee", null, FailReason.RESOLVER_ERROR, RuntimeException("down"))
        sink.onFailClosed("account", "ssn", FailReason.HIDE_NON_NULLABLE, null)

        assertThat(
            registry.counter("ihawu.masking.failures", "resource", "employee", "reason", "RESOLVER_ERROR").count(),
        ).isEqualTo(2.0)
        assertThat(
            registry.counter("ihawu.masking.failures", "resource", "account", "reason", "HIDE_NON_NULLABLE").count(),
        ).isEqualTo(1.0)
    }

    @Test
    fun `composite sink fans out to every delegate in order`() {
        val calls = mutableListOf<String>()
        val a = MaskingFailureSink { resource, _, _, _ -> calls += "a:$resource" }
        val b = MaskingFailureSink { resource, _, _, _ -> calls += "b:$resource" }

        CompositeMaskingFailureSink(a, b).onFailClosed("employee", null, FailReason.RESOLVER_ERROR, null)

        assertThat(calls).containsExactly("a:employee", "b:employee")
    }
}
