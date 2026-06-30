package com.ihawu.spring.boot.starter.autoconfiguration

import com.ihawu.core.policy.IhawuPrincipal
import com.ihawu.spring.boot.starter.configuration.IhawuSecurityConfig
import com.ihawu.spring.boot.starter.security.IhawuPrincipalCaptureFilter
import com.ihawu.spring.boot.starter.security.PrincipalResolver
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.security.core.Authentication
import kotlin.test.Test

class IhawuSecurityAutoConfigTest {
    private val nonWebAppRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IhawuSecurityConfig::class.java))
    private val webAppRunner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IhawuSecurityConfig::class.java))

    @Test
    fun `non-web app does not register the principal resolver or capture filter`() {
        nonWebAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(PrincipalResolver::class.java)
            assertThat(context).doesNotHaveBean(IhawuPrincipalCaptureFilter::class.java)
        }
    }

    @Test
    fun `web app without spring security on the classpath registers no security beans`() {
        webAppRunner
            .withClassLoader(FilteredClassLoader(Authentication::class.java))
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(PrincipalResolver::class.java)
                assertThat(context).doesNotHaveBean(IhawuPrincipalCaptureFilter::class.java)
            }
    }

    @Test
    fun `web app with spring security registers the principal resolver and capture filter`() {
        webAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(PrincipalResolver::class.java)
            assertThat(context).hasSingleBean(IhawuPrincipalCaptureFilter::class.java)
        }
    }

    @Test
    fun `backs off the default principal resolver when the app supplies its own`() {
        val custom =
            object : PrincipalResolver {
                override fun resolve(authentication: Authentication): IhawuPrincipal? = null
            }

        webAppRunner
            .withBean(PrincipalResolver::class.java, { custom })
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(PrincipalResolver::class.java)
                assertThat(context.getBean(PrincipalResolver::class.java)).isSameAs(custom)
                assertThat(context).hasSingleBean(IhawuPrincipalCaptureFilter::class.java)
            }
    }
}
