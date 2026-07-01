package org.ihawu.sample

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

/**
 * Masking driven by the programmatic [PolicyConfig] provider bean (`provider` profile).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("provider")
class ProviderProfileMaskingTest(
    @Autowired mockMvc: MockMvc,
) : AbstractEmployeeMaskingTest(mockMvc)
