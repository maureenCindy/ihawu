package org.ihawu.core.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JavaType

/**
 * How a single field may be masked so the output still satisfies the field's declared type contract.
 *
 * Derived once per type from the declared type and its Kotlin nullability. Two orthogonal facts —
 * textual-or-not and nullable-or-not — decide what each strategy may do: REDACT depends on
 * textual-vs-not (a placeholder for text, JSON null for a nullable non-text field, rejected
 * otherwise); HIDE depends only on nullability (see [omittable]).
 */
internal enum class MaskingCapability {
    /** Textual, non-null. REDACT -> placeholder; HIDE -> reject (omitting a required field breaks the schema). */
    TEXTUAL_REQUIRED,

    /** Textual, nullable. REDACT -> placeholder; HIDE -> omit. */
    TEXTUAL_OPTIONAL,

    /** Non-textual, nullable. REDACT -> JSON null; HIDE -> omit. */
    NULLABLE,

    /** Non-textual, non-null. No contract-safe masked form exists; both strategies reject. */
    UNSAFE,
    ;

    /**
     * REDACT: writes the masked value of [fieldName], owning the field-name write. Returns `false`
     * when the field is [UNSAFE] and must be omitted, so the caller can log a fail-closed diagnostic.
     */
    fun writeRedacted(
        gen: JsonGenerator,
        fieldName: String,
        placeholder: String,
    ): Boolean =
        when (this) {
            TEXTUAL_REQUIRED, TEXTUAL_OPTIONAL -> {
                gen.writeFieldName(fieldName)
                gen.writeString(placeholder)
                true
            }
            NULLABLE -> {
                gen.writeFieldName(fieldName)
                gen.writeNull()
                true
            }
            UNSAFE -> false
        }

    /** HIDE: whether this field may be omitted without breaking the declared schema. */
    val omittable: Boolean
        get() = this == TEXTUAL_OPTIONAL || this == NULLABLE

    companion object {
        fun of(
            type: JavaType,
            nullable: Boolean,
        ): MaskingCapability =
            when {
                type.isTypeOrSubTypeOf(CharSequence::class.java) ->
                    if (nullable) TEXTUAL_OPTIONAL else TEXTUAL_REQUIRED
                nullable -> NULLABLE
                else -> UNSAFE
            }
    }
}
