package org.ihawu.spring.boot.starter.fixture

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

val testEmployee =
    TestEmployee(
        "Cindy",
        "1234",
        1000.0,
        contacts =
            listOf(
                Contact(
                    isPrimaryContact = true,
                    phone = "123456789",
                    email = "cindy@example.com",
                    homeAddress = "B12 Eland Dr",
                ),
            ),
    )

@RestController
class EmployeeTestController {
    @GetMapping("/employee")
    fun getEmployee(): TestEmployee = testEmployee

    @GetMapping("/public/employee")
    fun getPublicEmployee(): TestEmployee = testEmployee

    /** Returns many employees of the same resource type, to exercise per-request policy caching. */
    @GetMapping("/employees")
    fun getEmployees(): List<TestEmployee> = List(5) { testEmployee.copy(contacts = emptyList()) }
}
