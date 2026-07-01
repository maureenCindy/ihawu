package org.ihawu.sample

import org.ihawu.core.annotation.IhawuResource

/**
 * The HR employee record returned by [EmployeeController].
 *
 * Annotated `@IhawuResource("employee")`, so Ihawu masks its fields at serialization time according to
 * the calling role's policy (see `PolicyConfig`). The sensitive fields — [salary],
 * [socialSecurityNumber], [performanceNotes] — are the ones rules restrict; the rest stay visible.
 */
@IhawuResource("employee")
data class EmployeeResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val salary: Double,
    val socialSecurityNumber: String,
    val performanceNotes: String,
)
