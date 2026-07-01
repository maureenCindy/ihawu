package com.ihawu.spring.boot.starter.autoconfiguration

import com.ihawu.core.masking.MaskingStrategy
import com.ihawu.core.policy.FieldPolicy
import com.ihawu.core.policy.IhawuPrincipal
import com.ihawu.core.policy.ResourcePolicy
import com.ihawu.core.policy.ResourcePolicyResolver
import com.ihawu.core.policy.RoleBasedResourcePolicyResolver
import com.ihawu.spring.boot.starter.configuration.IhawuProperties
import com.ihawu.spring.boot.starter.configuration.ResourcePolicyConfig
import com.ihawu.spring.boot.starter.configuration.ResourcePolicyProvider
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.WebApplicationContext
import kotlin.test.Test

class IhawuResourcePolicyAutoConfigTest {
    // ResourcePolicyConfig's default provider depends on IhawuProperties, which the full
    // auto-configuration enables; supply it here so the config can be tested in isolation.
    private val nonWebAppRunner =
        ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig::class.java)
            .withConfiguration(AutoConfigurations.of(ResourcePolicyConfig::class.java))
    private val webAppRunner =
        WebApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig::class.java)
            .withConfiguration(AutoConfigurations.of(ResourcePolicyConfig::class.java))

    @Test
    fun `non-web app registers the plain resolver and excludes the request-scoped caching resolver`() {
        nonWebAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(ResourcePolicyResolver::class.java)
            assertThat(context).doesNotHaveBean("cachingResourcePolicyResolver")
        }
    }

    @Test
    fun `registers a default RoleBased resolver when the app supplies none`() {
        nonWebAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(ResourcePolicyResolver::class.java)
            assertThat(context.getBean(ResourcePolicyResolver::class.java))
                .isInstanceOf(RoleBasedResourcePolicyResolver::class.java)
        }
    }

    @Test
    fun `backs off the default RoleBased resolver when the app supplies its own`() {
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

        nonWebAppRunner
            .withBean(ResourcePolicyResolver::class.java, { custom })
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(ResourcePolicyResolver::class.java)
                assertThat(context.getBean(ResourcePolicyResolver::class.java)).isSameAs(custom)
            }
    }

    @Test
    fun `web app registers the default RoleBased resolver`() {
        webAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasBean("resourcePolicyResolver")
            assertThat(context.getBean("resourcePolicyResolver"))
                .isInstanceOf(RoleBasedResourcePolicyResolver::class.java)
        }
    }

    @Test
    fun `web app registers the caching resolver as primary`() {
        webAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasBean("cachingResourcePolicyResolver")
            assertThat(
                context.beanFactory
                    .getBeanDefinition("cachingResourcePolicyResolver")
                    .isPrimary,
            ).isTrue
        }
    }

    @Test
    fun `web app scopes the caching resolver to the request`() {
        webAppRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(
                context.beanFactory
                    .getBeanDefinition("scopedTarget.cachingResourcePolicyResolver")
                    .scope,
            ).isEqualTo(WebApplicationContext.SCOPE_REQUEST)
        }
    }

    @Test
    fun `binds ihawu_policies into the provider as core ResourcePolicy rules`() {
        nonWebAppRunner
            .withPropertyValues(
                "ihawu.policies[0].resource=employee",
                "ihawu.policies[0].roles[ADMIN][0].field=ssn",
                "ihawu.policies[0].roles[ADMIN][0].strategy=REDACT",
                "ihawu.policies[0].roles[ADMIN][0].placeholder=***ssn",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(ResourcePolicyProvider::class.java).getResourcePolicies())
                    .containsExactly(
                        ResourcePolicy(
                            "employee",
                            mapOf("ADMIN" to listOf(FieldPolicy("ssn", MaskingStrategy.REDACT, "***ssn"))),
                        ),
                    )
            }
    }

    @Test
    fun `defaults an omitted strategy to HIDE when binding ihawu_policies`() {
        nonWebAppRunner
            .withPropertyValues(
                "ihawu.policies[0].resource=employee",
                "ihawu.policies[0].roles[ADMIN][0].field=salary",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(ResourcePolicyProvider::class.java).getResourcePolicies())
                    .containsExactly(
                        ResourcePolicy(
                            "employee",
                            mapOf("ADMIN" to listOf(FieldPolicy("salary", MaskingStrategy.HIDE, null))),
                        ),
                    )
            }
    }

    @Test
    fun `fails fast at startup when ihawu_policies has duplicate resource keys`() {
        nonWebAppRunner
            .withPropertyValues(
                "ihawu.policies[0].resource=employee",
                "ihawu.policies[1].resource=employee",
            ).run { context ->
                assertThat(context).hasFailed()
                assertThat(context).getFailure().hasRootCauseInstanceOf(IllegalArgumentException::class.java)
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IhawuProperties::class)
    class PropertiesConfig
}
