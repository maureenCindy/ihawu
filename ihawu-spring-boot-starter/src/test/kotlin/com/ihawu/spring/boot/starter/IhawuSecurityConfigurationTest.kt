package com.ihawu.spring.boot.starter

import com.ihawu.core.policy.IhawuPrincipal
import com.ihawu.spring.boot.starter.security.IhawuRequestFilter
import com.ihawu.spring.boot.starter.security.PrincipalResolver
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.core.Authentication
import kotlin.test.Test

class IhawuSecurityConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IhawuAutoConfiguration::class.java))

    @Test
    fun `registers the principal resolver and request filter when Spring Security is present`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(PrincipalResolver::class.java)
            assertThat(context).hasSingleBean(IhawuRequestFilter::class.java)
        }
    }

    @Test
    fun `backs off and still loads when Spring Security is absent`() {
        runner
            .withClassLoader(FilteredClassLoader(Authentication::class.java))
            .run { context ->
                assertThat(context).hasNotFailed() // the starter still loads without Spring Security
                assertThat(context).doesNotHaveBean(PrincipalResolver::class.java)
                assertThat(context).doesNotHaveBean(IhawuRequestFilter::class.java)
            }
    }

    @Test
    fun `backs off the default resolver when the application supplies its own`() {
        val custom =
            object : PrincipalResolver {
                override fun resolve(authentication: Authentication): IhawuPrincipal? = null
            }

        runner
            .withBean(PrincipalResolver::class.java, { custom })
            .run { context ->
                assertThat(context).hasSingleBean(PrincipalResolver::class.java)
                assertThat(context.getBean(PrincipalResolver::class.java)).isSameAs(custom)
            }
    }
}
