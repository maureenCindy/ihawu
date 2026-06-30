package com.ihawu.spring.boot.starter.autoconfiguration

import com.ihawu.spring.boot.starter.IhawuAutoConfiguration
import com.ihawu.spring.boot.starter.configuration.IhawuProperties
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import kotlin.test.Test

class IhawuAutoConfigurationTest {
    private val nonWebAppRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IhawuAutoConfiguration::class.java))
    private val webAppRunner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IhawuAutoConfiguration::class.java))

    @Test
    fun `non-web app registers the auto-configuration and properties by default`() {
        nonWebAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(IhawuAutoConfiguration::class.java)
            assertThat(context).hasSingleBean(IhawuProperties::class.java)
        }
    }

    @Test
    fun `web app registers the auto-configuration and properties by default`() {
        webAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(IhawuAutoConfiguration::class.java)
            assertThat(context).hasSingleBean(IhawuProperties::class.java)
        }
    }

    @Test
    fun `non-web app backs off entirely when ihawu_enabled is false`() {
        nonWebAppRunner
            .withPropertyValues("ihawu.enabled=false")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(IhawuAutoConfiguration::class.java)
                assertThat(context).doesNotHaveBean(IhawuProperties::class.java)
            }
    }

    @Test
    fun `web app backs off entirely when ihawu_enabled is false`() {
        webAppRunner
            .withPropertyValues("ihawu.enabled=false")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(IhawuAutoConfiguration::class.java)
                assertThat(context).doesNotHaveBean(IhawuProperties::class.java)
            }
    }
}
