package com.ihawu.spring.boot.starter.masking

import com.ihawu.spring.boot.starter.fixture.EmployeeTestController
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * End-to-end proof that masking rules declared in `ihawu.policies` configuration drive the response,
 * with no programmatic `ResourcePolicyProvider`: the config flows through the default
 * `ConfigResourcePolicyProvider` → `RoleBasedResourcePolicyResolver` → serialization.
 */
@SpringBootTest(
    classes = [ConfigPropertiesMaskingTest.ConfigDrivenMaskingApp::class],
    properties = [
        "ihawu.policies[0].resource=employee",
        "ihawu.policies[0].roles[ADMIN][0].field=ssn",
        "ihawu.policies[0].roles[ADMIN][0].strategy=REDACT",
        "ihawu.policies[0].roles[ADMIN][0].placeholder=***ssn",
        "ihawu.policies[0].roles[ADMIN][1].field=salary",
        "ihawu.policies[0].roles[ADMIN][1].strategy=HIDE",
    ],
)
@AutoConfigureMockMvc
class ConfigPropertiesMaskingTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `masks fields according to the ihawu_policies configuration`() {
        mockMvc
            .perform(MockMvcRequestBuilders.get("/employee"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Cindy"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.ssn").value("***ssn"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.salary").doesNotExist())
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(EmployeeTestController::class)
    class ConfigDrivenMaskingApp {
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
    }
}
