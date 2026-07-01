package com.ihawu.spring.boot.starter.masking

import com.ihawu.core.masking.MaskingStrategy
import com.ihawu.core.policy.FieldPolicy
import com.ihawu.core.policy.ResourcePolicy
import com.ihawu.spring.boot.starter.configuration.ResourcePolicyProvider
import com.ihawu.spring.boot.starter.fixture.EmployeeTestController
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    classes = [ProviderDrivenMaskingTest.ProviderDrivenMaskingWebApp::class],
)
@AutoConfigureMockMvc
class ProviderDrivenMaskingTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    @WithMockUser(
        username = "admin",
        roles = ["ADMIN"],
    )
    fun `mask employee data when role is ADMIN`() {
        mockMvc
            .perform(
                get("/employee"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Cindy"))
            .andExpect(jsonPath("$.ssn").value("***ssn"))
            .andExpect(jsonPath("$.salary").doesNotExist())
            .andExpect(jsonPath("$.contacts", hasSize<Int>(1)))
            .andExpect(jsonPath("$.contacts[0].primaryContact").value(true))
            .andExpect(jsonPath("$.contacts[0].email").value("cindy@example.com"))
            .andExpect(jsonPath("$.contacts[0].phone").value("+263***"))
            .andExpect(jsonPath("$.contacts[0].homeAddress").doesNotExist())
    }

    @Test
    @WithMockUser(
        username = "user",
        roles = ["USER"],
    )
    fun `user role with no masking rules sees whole object`() {
        mockMvc
            .perform(
                get("/employee"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Cindy"))
            .andExpect(jsonPath("$.ssn").value("1234"))
            .andExpect(jsonPath("$.salary").value(1000.0))
            .andExpect(jsonPath("$.contacts", hasSize<Int>(1)))
            .andExpect(jsonPath("$.contacts[0].primaryContact").value(true))
            .andExpect(jsonPath("$.contacts[0].email").value("cindy@example.com"))
            .andExpect(jsonPath("$.contacts[0].phone").value("123456789"))
            .andExpect(jsonPath("$.contacts[0].homeAddress").value("B12 Eland Dr"))
    }

    @Test
    fun `anonymous user gets null response on protected resource`() {
        mockMvc
            .perform(
                get("/public/employee"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").doesNotExist())
            .andExpect(jsonPath("$.ssn").doesNotExist())
            .andExpect(jsonPath("$.salary").doesNotExist())
            .andExpect(jsonPath("$.contacts").doesNotExist())
    }

    /**
     * A web app supplying **no** `ResourcePolicyProvider`.
     * Uses `@EnableAutoConfiguration` + an explicit [org.springframework.context.annotation.Import] of the controller
     * (not `@SpringBootApplication` scanning) so it stays isolated from the other fixture apps.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(EmployeeTestController::class)
    class ProviderDrivenMaskingWebApp {
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
            ResourcePolicyProvider {
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
}
