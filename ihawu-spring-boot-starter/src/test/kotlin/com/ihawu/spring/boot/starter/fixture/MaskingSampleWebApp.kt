package com.ihawu.spring.boot.starter.fixture

import com.ihawu.core.masking.MaskingStrategy
import com.ihawu.core.policy.FieldPolicy
import com.ihawu.core.policy.ResourcePolicy
import com.ihawu.spring.boot.starter.configuration.ResourcePolicyProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@SpringBootApplication
class MaskingSampleWebApp {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests {
                it
                    .requestMatchers("/public/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.build()

    @Bean
    fun resourcePolicyProvider(): ResourcePolicyProvider =
        object : ResourcePolicyProvider {
            override fun getResourcePolicies(): List<ResourcePolicy> =
                listOf(
                    ResourcePolicy(
                        resourceName = "employee",
                        roleFieldPolicies =
                            mapOf(
                                "ADMIN" to
                                    listOf(
                                        FieldPolicy("salary", MaskingStrategy.HIDE),
                                        FieldPolicy("ssn", MaskingStrategy.REDACT, "***ssn"),
                                    ),
                            ),
                    ),
                    ResourcePolicy(
                        resourceName = "contact",
                        roleFieldPolicies =
                            mapOf(
                                "ADMIN" to
                                    listOf(
                                        FieldPolicy("homeAddress", MaskingStrategy.HIDE),
                                        FieldPolicy("phone", MaskingStrategy.REDACT, "+263***"),
                                    ),
                            ),
                    ),
                )
        }
}
