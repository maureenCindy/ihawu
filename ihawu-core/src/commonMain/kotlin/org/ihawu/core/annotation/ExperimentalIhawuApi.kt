package org.ihawu.core.annotation

/**
 * Marks Ihawu API that is **experimental**: it may change or be removed in any release, including a
 * minor or patch, without a deprecation cycle — the 1.0 compatibility promise (see `COMPATIBILITY.md`)
 * deliberately does not cover it. Experimental declarations are also excluded from the checked-in
 * binary-compatibility dumps for the same reason.
 *
 * Using such API requires an explicit opt-in, so the instability is a choice, never a surprise:
 *
 * ```
 * @OptIn(ExperimentalIhawuApi::class)
 * ```
 *
 * New features may ship under this marker to gather feedback before their shape freezes; graduation
 * removes the annotation (a non-breaking change) and brings the declaration under the compatibility
 * promise from that release on.
 */
@RequiresOptIn(
    message = "This Ihawu API is experimental and may change or be removed without a deprecation cycle.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalIhawuApi
