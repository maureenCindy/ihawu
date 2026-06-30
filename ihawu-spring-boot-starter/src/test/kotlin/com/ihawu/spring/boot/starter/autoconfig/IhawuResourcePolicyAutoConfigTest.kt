package com.ihawu.spring.boot.starter.autoconfig

import com.ihawu.core.masking.MaskingStrategy
import com.ihawu.core.policy.FieldPolicy
import com.ihawu.core.policy.IhawuPrincipal
import com.ihawu.core.policy.ResourcePolicyResolver
import com.ihawu.core.policy.RoleBasedResourcePolicyResolver
import com.ihawu.spring.boot.starter.configuration.ResourcePolicyConfig
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.web.context.WebApplicationContext
import kotlin.test.Test

class IhawuResourcePolicyAutoConfigTest {
    private val nonWebAppRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ResourcePolicyConfig::class.java))
    private val webAppRunner =
        WebApplicationContextRunner()
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
}
