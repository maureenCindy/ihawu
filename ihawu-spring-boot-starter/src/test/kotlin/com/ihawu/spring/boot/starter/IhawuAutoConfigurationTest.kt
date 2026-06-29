package com.ihawu.spring.boot.starter

import com.ihawu.core.masking.MaskingStrategy
import com.ihawu.core.policy.FieldPolicy
import com.ihawu.core.policy.IhawuPrincipal
import com.ihawu.core.policy.ResourcePolicyResolver
import com.ihawu.core.serialization.IhawuModule
import com.ihawu.spring.boot.starter.security.PrincipalResolver
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.security.core.Authentication
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
    private val webRunner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IhawuAutoConfiguration::class.java))

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

    @Test
    fun `core masking beans always present`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(IhawuModule::class.java)
            assertThat(context).hasSingleBean(ResourcePolicyResolver::class.java)
        }
    }

    @Test
    fun `web app registers the ihawu web mvc configurer`() {
        webRunner.run { context ->
            assertThat(context).hasBean("ihawuWebMvcConfigurer")
        }
    }

    @Test
    fun `non-web app does not register the web mvc configurer`() {
        contextRunner.run { context -> assertThat(context).doesNotHaveBean("ihawuWebMvcConfigurer") }
    }

    @Test
    fun `without spring security the context loads and core masking beans remain`() {
        contextRunner
            .withClassLoader(FilteredClassLoader(Authentication::class.java))
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(PrincipalResolver::class.java)
                assertThat(context).hasSingleBean(IhawuModule::class.java)
            }
    }

    @Test
    fun `in-built resource policy resolver backs off when custom resource policy resolver is provided`() {
        val custom =
            object : ResourcePolicyResolver {
                override fun resolve(
                    principal: IhawuPrincipal,
                    resource: String,
                ): List<FieldPolicy> =
                    listOf(
                        FieldPolicy("ssn", MaskingStrategy.HIDE),
                    )
            }

        contextRunner
            .withBean(ResourcePolicyResolver::class.java, { custom })
            .run { context ->
                assertThat(context).hasSingleBean(ResourcePolicyResolver::class.java)
                assertThat(context.getBean(ResourcePolicyResolver::class.java)).isSameAs(custom)
            }
    }
}
