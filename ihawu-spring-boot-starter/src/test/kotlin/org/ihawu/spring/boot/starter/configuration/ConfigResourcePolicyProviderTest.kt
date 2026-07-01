package org.ihawu.spring.boot.starter.configuration

import org.assertj.core.api.Assertions
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.ResourcePolicy
import kotlin.test.Test

class ConfigResourcePolicyProviderTest {
    @Test
    fun `maps configured policies to core ResourcePolicy rules`() {
        val provider =
            ConfigResourcePolicyProvider(
                listOf(
                    IhawuProperties.PolicyProperties(
                        resource = "employee",
                        roles =
                            mapOf(
                                "ADMIN" to
                                    listOf(
                                        IhawuProperties.FieldPolicyProperties("salary", MaskingStrategy.HIDE),
                                        IhawuProperties.FieldPolicyProperties(
                                            "ssn",
                                            MaskingStrategy.REDACT,
                                            "***ssn",
                                        ),
                                    ),
                            ),
                    ),
                ),
            )

        Assertions.assertThat(provider.getResourcePolicies()).containsExactly(
            ResourcePolicy(
                "employee",
                mapOf(
                    "ADMIN" to
                        listOf(
                            FieldPolicy("salary", MaskingStrategy.HIDE),
                            FieldPolicy("ssn", MaskingStrategy.REDACT, "***ssn"),
                        ),
                ),
            ),
        )
    }

    @Test
    fun `returns no policies when configuration is empty`() {
        Assertions.assertThat(ConfigResourcePolicyProvider(emptyList()).getResourcePolicies()).isEmpty()
    }

    @Test
    fun `maps a resource with no roles to an empty rule set`() {
        val provider = ConfigResourcePolicyProvider(listOf(IhawuProperties.PolicyProperties("employee")))

        Assertions
            .assertThat(provider.getResourcePolicies())
            .containsExactly(ResourcePolicy("employee", emptyMap()))
    }

    @Test
    fun `rejects duplicate resource keys`() {
        Assertions
            .assertThatThrownBy {
                ConfigResourcePolicyProvider(
                    listOf(IhawuProperties.PolicyProperties("employee"), IhawuProperties.PolicyProperties("employee")),
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("duplicate resource keys")
            .hasMessageContaining("employee")
    }

    @Test
    fun `rejects a blank field name`() {
        Assertions
            .assertThatThrownBy {
                ConfigResourcePolicyProvider(
                    listOf(
                        IhawuProperties.PolicyProperties(
                            resource = "employee",
                            roles = mapOf("ADMIN" to listOf(IhawuProperties.FieldPolicyProperties(" "))),
                        ),
                    ),
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("blank field name")
            .hasMessageContaining("employee")
            .hasMessageContaining("ADMIN")
    }
}
