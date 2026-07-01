package org.ihawu.spring.boot.starter.autoconfiguration

import org.assertj.core.api.Assertions.assertThat
import org.ihawu.spring.boot.starter.configuration.IhawuWebConfig
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import kotlin.test.Test

class IhawuWebAutoConfigTest {
    private val nonWebAppRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IhawuWebConfig::class.java))
    private val webAppRunner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IhawuWebConfig::class.java))

    @Test
    fun `non-web app context does not register the ihawu web mvc configurer`() {
        nonWebAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean("ihawuWebMvcConfigurer")
        }
    }

    @Test
    fun `web app context registers the ihawu web mvc configurer`() {
        webAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasBean("ihawuWebMvcConfigurer")
        }
    }

    @Test
    fun `other WebMvcConfigurer beans do not suppress the ihawu web mvc configurer`() {
        webAppRunner
            .withBean(
                "appWebMvcConfigurer",
                WebMvcConfigurer::class.java,
                { object : WebMvcConfigurer {} },
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasBean("ihawuWebMvcConfigurer")
            }
    }

    @Test
    fun `backs off the ihawu web mvc configurer when the app supplies one by that name`() {
        val custom = object : WebMvcConfigurer {}

        webAppRunner
            .withBean("ihawuWebMvcConfigurer", WebMvcConfigurer::class.java, { custom })
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasBean("ihawuWebMvcConfigurer")
                assertThat(context.getBean("ihawuWebMvcConfigurer")).isSameAs(custom)
            }
    }
}
