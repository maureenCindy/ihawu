package com.ihawu.spring.boot.starter

import com.ihawu.spring.boot.starter.fixture.MaskingSampleWebApp
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    classes = [MaskingSampleWebApp::class],
)
@AutoConfigureMockMvc
class IhawuResourceMaskingTest(
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
}
