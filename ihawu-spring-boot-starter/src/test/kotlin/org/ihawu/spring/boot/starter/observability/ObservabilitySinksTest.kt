package org.ihawu.spring.boot.starter.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.ihawu.core.masking.FailReason
import org.ihawu.core.masking.MaskingFailure
import org.ihawu.core.masking.MaskingFailureSink
import kotlin.test.Test

class ObservabilitySinksTest {
    @Test
    fun `micrometer sink counts failures tagged by resource and reason`() {
        val registry = SimpleMeterRegistry()
        val sink = MicrometerMaskingFailureSink(registry)

        sink.onFailClosed(MaskingFailure("employee", FailReason.RESOLVER_ERROR, cause = RuntimeException("down")))
        sink.onFailClosed(MaskingFailure("employee", FailReason.RESOLVER_ERROR, cause = RuntimeException("down")))
        sink.onFailClosed(MaskingFailure("account", FailReason.HIDE_NON_NULLABLE, field = "ssn"))

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
        val a = MaskingFailureSink { failure -> calls += "a:${failure.resource}" }
        val b = MaskingFailureSink { failure -> calls += "b:${failure.resource}" }

        CompositeMaskingFailureSink(a, b).onFailClosed(MaskingFailure("employee", FailReason.RESOLVER_ERROR))

        assertThat(calls).containsExactly("a:employee", "b:employee")
    }
}
