package com.ihawu.samples.annotation

import com.ihawu.core.annotation.IhawuResource

fun annotatedEmployeeProfile() {
    @IhawuResource(name = "employee-profile")
    data class EmployeeProfile(
        val fullName: String,
        // sensitive fields
        val idNumber: String,
        val salary: Double,
    )
}
