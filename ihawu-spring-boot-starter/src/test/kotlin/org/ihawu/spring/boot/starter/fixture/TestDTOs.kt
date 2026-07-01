package org.ihawu.spring.boot.starter.fixture

import org.ihawu.core.annotation.IhawuResource

@IhawuResource("employee")
data class TestEmployee(
    val name: String,
    val ssn: String,
    val salary: Double,
    val contacts: List<Contact>,
)

@IhawuResource("contact")
data class Contact(
    val isPrimaryContact: Boolean,
    val phone: String,
    val email: String? = null,
    val homeAddress: String? = null,
)
