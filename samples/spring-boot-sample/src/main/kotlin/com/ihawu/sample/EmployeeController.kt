package com.ihawu.sample

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Secured endpoint returning an [EmployeeResponse].
 *
 * The controller returns the full record and stays free of any authorization or masking logic — Ihawu
 * enforces the calling role's policy as the response is serialized, so `HR_ADMIN`, `MANAGER`, and
 * `EMPLOYEE` callers receive different field visibility from the very same handler.
 */
@RestController
@RequestMapping("/employees")
class EmployeeController {
    @GetMapping("/{id}")
    fun getEmployee(
        @PathVariable id: String,
    ): EmployeeResponse =
        EmployeeResponse(
            id = id,
            fullName = "Jane Doe",
            email = "jane.doe@company.com",
            salary = 145_000.0,
            socialSecurityNumber = "123-45-6789",
            performanceNotes = "Exceeds expectations; promotion track.",
        )
}
