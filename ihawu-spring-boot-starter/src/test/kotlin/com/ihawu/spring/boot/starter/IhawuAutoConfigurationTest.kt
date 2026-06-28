package com.ihawu.spring.boot.starter

import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test

class IhawuAutoConfigurationTest {
    // ApplicationContextRunner is the standard way to test auto-configurations
    // It creates a lightweight Spring context without starting a web server
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    IhawuAutoConfiguration::class.java,
                ),
            )

    @Test
    fun `backs off when ihawu_enabled is false`() {
        contextRunner
            .withPropertyValues("ihawu.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(IhawuAutoConfiguration::class.java)
                assertThat(context).doesNotHaveBean(IhawuProperties::class.java)
            }
    }

    @Test
    fun `loads by default when ihawu_enabled is absent`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(IhawuAutoConfiguration::class.java)
            assertThat(context).hasSingleBean(IhawuProperties::class.java)
        }
    }
}
