package org.ihawu.spring.boot.starter.autoconfiguration

import org.assertj.core.api.Assertions.assertThat
import org.ihawu.core.exception.IhawuCoreException
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.ResourcePolicy
import org.ihawu.spring.boot.starter.configuration.IhawuProperties
import org.ihawu.spring.boot.starter.configuration.MaskingContractStartupValidator
import org.ihawu.spring.boot.starter.configuration.MaskingContractValidationConfig
import org.ihawu.spring.boot.starter.configuration.ResourcePolicyProvider
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import kotlin.test.Test

class MaskingContractValidationAutoConfigTest {
    private val fixtures = "org.ihawu.spring.boot.starter.fixture"

    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))
            .withUserConfiguration(PropertiesConfig::class.java, MaskingContractValidationConfig::class.java)

    private fun policy(vararg fields: FieldPolicy): ResourcePolicyProvider =
        ResourcePolicyProvider { listOf(ResourcePolicy("employee", mapOf("ADMIN" to fields.toList()))) }

    @Test
    fun `fails startup when a policy REDACTs a non-nullable non-String field`() {
        runner
            // TestEmployee.salary is a non-nullable Double -> no contract-safe redaction.
            .withBean(ResourcePolicyProvider::class.java, { policy(FieldPolicy("salary", MaskingStrategy.REDACT, "***")) })
            .withPropertyValues("ihawu.resource-base-packages=$fixtures")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context).getFailure().isInstanceOf(IhawuCoreException::class.java)
                assertThat(context).getFailure().hasStackTraceContaining("employee.salary")
            }
    }

    @Test
    fun `fails startup when a policy HIDEs a non-nullable field`() {
        runner
            // TestEmployee.name is a non-nullable String -> omitting it breaks the schema.
            .withBean(ResourcePolicyProvider::class.java, { policy(FieldPolicy("name", MaskingStrategy.HIDE)) })
            .withPropertyValues("ihawu.resource-base-packages=$fixtures")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context).getFailure().hasStackTraceContaining("employee.name")
            }
    }

    @Test
    fun `starts when every policy satisfies the type contract`() {
        runner
            // ssn: non-null String REDACT (ok); homeAddress: nullable String HIDE (ok, on Contact).
            .withBean(ResourcePolicyProvider::class.java, { policy(FieldPolicy("ssn", MaskingStrategy.REDACT, "***ssn")) })
            .withPropertyValues("ihawu.resource-base-packages=$fixtures")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(MaskingContractStartupValidator::class.java)
            }
    }

    @Test
    fun `backs off and skips validation when disabled`() {
        runner
            .withBean(ResourcePolicyProvider::class.java, { policy(FieldPolicy("salary", MaskingStrategy.REDACT, "***")) })
            .withPropertyValues(
                "ihawu.resource-base-packages=$fixtures",
                "ihawu.validate-resource-contract=false",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(MaskingContractStartupValidator::class.java)
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IhawuProperties::class)
    class PropertiesConfig
}
