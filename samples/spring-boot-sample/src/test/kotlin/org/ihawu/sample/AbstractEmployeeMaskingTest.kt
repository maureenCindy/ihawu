package org.ihawu.sample

import org.junit.jupiter.api.Test
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Shared masking assertions for the sample's `/employees/{id}` endpoint.
 *
 * Concrete subclasses activate a policy-source profile (`provider` or `config`) and run these same
 * assertions — proving the two ways of supplying policy produce identical masked output.
 */
abstract class AbstractEmployeeMaskingTest(
    private val mockMvc: MockMvc,
) {
    @Test
    @WithMockUser(username = "hradmin", roles = ["HR_ADMIN"])
    fun `HR admin sees the full employee record`() {
        mockMvc
            .perform(get("/employees/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fullName").value("Jane Doe"))
            .andExpect(jsonPath("$.salary").value(145000.0))
            .andExpect(jsonPath("$.socialSecurityNumber").value("123-45-6789"))
            .andExpect(jsonPath("$.performanceNotes").value("Exceeds expectations; promotion track."))
    }

    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `manager sees salary and notes but a redacted SSN`() {
        mockMvc
            .perform(get("/employees/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.salary").value(145000.0))
            .andExpect(jsonPath("$.performanceNotes").value("Exceeds expectations; promotion track."))
            .andExpect(jsonPath("$.socialSecurityNumber").value("***-**-****"))
    }

    @Test
    @WithMockUser(username = "employee", roles = ["EMPLOYEE"])
    fun `employee sees only non-sensitive fields`() {
        mockMvc
            .perform(get("/employees/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fullName").value("Jane Doe"))
            .andExpect(jsonPath("$.email").value("jane.doe@company.com"))
            .andExpect(jsonPath("$.salary").doesNotExist())
            .andExpect(jsonPath("$.socialSecurityNumber").doesNotExist())
            .andExpect(jsonPath("$.performanceNotes").doesNotExist())
    }
}
