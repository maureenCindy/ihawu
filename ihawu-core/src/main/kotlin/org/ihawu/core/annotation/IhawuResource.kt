package org.ihawu.core.annotation

/**
 * Marks a response DTO that Ihawu evaluates at the serialization boundary, masking its fields according to the policy
 * resolved for [name].
 *
 * @property name The resource key the PolicyResolver resolves field policies against.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class IhawuResource(
    val name: String,
)
