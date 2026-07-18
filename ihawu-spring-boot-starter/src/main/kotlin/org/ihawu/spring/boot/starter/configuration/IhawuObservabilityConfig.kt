package org.ihawu.spring.boot.starter.configuration

import io.micrometer.core.instrument.MeterRegistry
import org.ihawu.core.masking.MaskingFailureSink
import org.ihawu.jackson.Slf4jMaskingFailureSink
import org.ihawu.spring.boot.starter.observability.CompositeMaskingFailureSink
import org.ihawu.spring.boot.starter.observability.MicrometerMaskingFailureSink
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers a Micrometer-backed [MaskingFailureSink] when Micrometer is on the classpath **and** a
 * [MeterRegistry] bean exists, so fail-closed drops are counted as
 * `ihawu.masking.failures{resource,reason}`. The contributed sink is a composite (SLF4J + Micrometer), so
 * adding metrics never costs the log line. Backs off entirely if the application defines its own
 * [MaskingFailureSink] bean.
 *
 * When Micrometer is absent there is no sink bean here, and [IhawuJacksonObjectMapperConfig] falls back to
 * the plain [Slf4jMaskingFailureSink].
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MeterRegistry::class)
internal class IhawuObservabilityConfig {
    @Bean
    @ConditionalOnBean(MeterRegistry::class)
    @ConditionalOnMissingBean(MaskingFailureSink::class)
    fun ihawuMaskingFailureSink(registry: MeterRegistry): MaskingFailureSink =
        CompositeMaskingFailureSink(Slf4jMaskingFailureSink(), MicrometerMaskingFailureSink(registry))
}
