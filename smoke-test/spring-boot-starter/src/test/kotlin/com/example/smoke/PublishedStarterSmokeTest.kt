package com.example.smoke

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * End-to-end proof that the PUBLISHED starter is consumable: with only
 * `org.ihawu:ihawu-spring-boot-starter` on the classpath (from mavenLocal), the app auto-configures
 * Ihawu and masks a restricted field over HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublishedStarterSmokeTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `published starter auto-configures and masks a restricted field`() {
        mockMvc
            .perform(get("/account"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.owner").value("Jane Doe"))
            .andExpect(jsonPath("$.ssn").value("***"))
    }
}
