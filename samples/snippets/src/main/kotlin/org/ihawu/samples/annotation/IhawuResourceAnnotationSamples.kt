package org.ihawu.samples.annotation

import org.ihawu.core.annotation.IhawuResource

// `name` is the resource key that policies resolve against — it is what links this type to the
// FieldPolicy rules for "employee". Which fields get masked is decided by policy at serialization
// time, not by the annotation or by field order.
fun annotatedEmployeeProfile() {
    @IhawuResource(name = "employee")
    data class EmployeeProfile(
        val fullName: String,
        val idNumber: String,
        val salary: Double,
    )
}
