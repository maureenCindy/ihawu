package org.ihawu.spring.boot.starter.autoconfiguration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.ihawu.core.masking.MaskingFailureSink
import org.ihawu.core.masking.ResolverErrorMode
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver
import org.ihawu.jackson.IhawuModule
import org.ihawu.spring.boot.starter.configuration.IhawuJacksonObjectMapperConfig
import org.ihawu.spring.boot.starter.configuration.IhawuObservabilityConfig
import org.ihawu.spring.boot.starter.configuration.IhawuProperties
import org.ihawu.spring.boot.starter.observability.CompositeMaskingFailureSink
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import kotlin.test.Test

class IhawuObservabilityAutoConfigTest {
    private val resolver =
        object : ResourcePolicyResolver {
            override fun resolve(
                principal: IhawuPrincipal,
                resource: String,
            ): List<FieldPolicy> = emptyList()
        }

    // Wires the observability config + the module config together, with a resolver and IhawuProperties,
    // exactly as the auto-configuration composes them.
    private val runner =
        ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig::class.java)
            .withConfiguration(
                AutoConfigurations.of(
                    IhawuObservabilityConfig::class.java,
                    IhawuJacksonObjectMapperConfig::class.java,
                ),
            ).withBean(ResourcePolicyResolver::class.java, { resolver })

    @Test
    fun `registers a composite slf4j plus micrometer sink when a MeterRegistry is present`() {
        runner.withBean(MeterRegistry::class.java, { SimpleMeterRegistry() }).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(MaskingFailureSink::class.java)
            assertThat(context.getBean(MaskingFailureSink::class.java))
                .isInstanceOf(CompositeMaskingFailureSink::class.java)
            assertThat(context).hasSingleBean(IhawuModule::class.java)
        }
    }

    @Test
    fun `registers no sink and the module falls back to slf4j when no MeterRegistry`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(MaskingFailureSink::class.java)
            // The module is still built — IhawuJacksonObjectMapperConfig falls back to the slf4j sink.
            assertThat(context).hasSingleBean(IhawuModule::class.java)
        }
    }

    @Test
    fun `backs off when the application defines its own MaskingFailureSink`() {
        val custom = MaskingFailureSink { _, _, _, _ -> }

        runner
            .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
            .withBean("appSink", MaskingFailureSink::class.java, { custom })
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(MaskingFailureSink::class.java)
                assertThat(context.getBean(MaskingFailureSink::class.java)).isSameAs(custom)
            }
    }

    @Test
    fun `binds ihawu on-policy-failure to fail-request`() {
        runner.withPropertyValues("ihawu.on-policy-failure=fail-request").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(IhawuProperties::class.java).onPolicyFailure)
                .isEqualTo(ResolverErrorMode.FAIL_REQUEST)
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IhawuProperties::class)
    class PropertiesConfig
}
