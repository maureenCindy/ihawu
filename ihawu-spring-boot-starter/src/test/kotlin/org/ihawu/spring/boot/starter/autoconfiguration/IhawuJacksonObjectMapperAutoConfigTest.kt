package org.ihawu.spring.boot.starter.autoconfiguration

import org.assertj.core.api.Assertions.assertThat
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver
import org.ihawu.jackson.IhawuModule
import org.ihawu.spring.boot.starter.configuration.IhawuJacksonObjectMapperConfig
import org.ihawu.spring.boot.starter.configuration.IhawuProperties
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Configuration
import kotlin.test.Test

class IhawuJacksonObjectMapperAutoConfigTest {
    private val stubResolver =
        object : ResourcePolicyResolver {
            override fun resolve(
                principal: IhawuPrincipal,
                resource: String,
            ): List<FieldPolicy> = emptyList()
        }

    private val nonWebAppRunner =
        ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig::class.java)
            .withBean(ResourcePolicyResolver::class.java, { stubResolver })
            .withConfiguration(AutoConfigurations.of(IhawuJacksonObjectMapperConfig::class.java))
    private val webAppRunner =
        WebApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig::class.java)
            .withBean(ResourcePolicyResolver::class.java, { stubResolver })
            .withConfiguration(AutoConfigurations.of(IhawuJacksonObjectMapperConfig::class.java))

    @Test
    fun `registers the masking IhawuModule even in a non-web app`() {
        nonWebAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(IhawuModule::class.java)
        }
    }

    @Test
    fun `registers the masking IhawuModule in a web app`() {
        webAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(IhawuModule::class.java)
        }
    }

    @Test
    fun `backs off the default IhawuModule when the app supplies its own`() {
        val custom = IhawuModule(stubResolver)

        nonWebAppRunner
            .withBean(IhawuModule::class.java, { custom })
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(IhawuModule::class.java)
                assertThat(context.getBean(IhawuModule::class.java)).isSameAs(custom)
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IhawuProperties::class)
    class PropertiesConfig
}
