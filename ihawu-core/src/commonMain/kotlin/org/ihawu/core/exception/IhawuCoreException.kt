package org.ihawu.core.exception

/**
 * Base exception for all errors raised by the Ihawu core engine.
 *
 * Thrown when policy evaluation, field masking, or principal resolution
 * encounters an unrecoverable error. Extends [RuntimeException] so it
 * propagates transparently through framework interceptors and serialization
 * pipelines without requiring explicit catch declarations.
 *
 * @property message A human-readable description of what went wrong.
 * @property cause The underlying exception that triggered this error, if any.
 */
class IhawuCoreException(
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)
