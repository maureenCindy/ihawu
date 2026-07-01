package com.ihawu.sample

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

/**
 * Masking driven by `ihawu.policies` configuration (`config` profile) via the starter's default
 * config-backed provider.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("config")
class ConfigProfileMaskingTest(
    @Autowired mockMvc: MockMvc,
) : AbstractEmployeeMaskingTest(mockMvc)
