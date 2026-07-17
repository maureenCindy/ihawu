package org.ihawu.sample

import org.ihawu.core.annotation.IhawuResource

/**
 * The HR employee record returned by [EmployeeController].
 *
 * Annotated `@IhawuResource("employee")`, so Ihawu masks its fields at serialization time according to
 * the calling role's policy (see `PolicyConfig`). The sensitive fields — [salary],
 * [socialSecurityNumber], [performanceNotes] — are the ones rules restrict; the rest stay visible.
 *
 * Those maskable fields are declared **nullable**: masking must satisfy the declared type contract, so
 * a field a policy may `HIDE` (omit) or `REDACT` to `null` must be one the schema permits to be absent.
 * The starter validates this at startup — declaring a masked non-`String` (or `HIDE`) field non-nullable
 * fails the context (see ADR 0005). The never-masked fields ([id], [fullName], [email]) stay non-null.
 */
@IhawuResource("employee")
data class EmployeeResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val salary: Double?,
    val socialSecurityNumber: String?,
    val performanceNotes: String?,
)
