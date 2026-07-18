package org.ihawu.core.masking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaskingCapabilityTest {
    @Test
    fun `of classifies the two declared-type facts into the four capabilities`() {
        assertEquals(MaskingCapability.TEXTUAL_REQUIRED, MaskingCapability.of(isTextual = true, nullable = false))
        assertEquals(MaskingCapability.TEXTUAL_OPTIONAL, MaskingCapability.of(isTextual = true, nullable = true))
        assertEquals(MaskingCapability.NULLABLE, MaskingCapability.of(isTextual = false, nullable = true))
        assertEquals(MaskingCapability.UNSAFE, MaskingCapability.of(isTextual = false, nullable = false))
    }

    @Test
    fun `omittable is true only for nullable or optional fields`() {
        assertTrue(MaskingCapability.TEXTUAL_OPTIONAL.omittable)
        assertTrue(MaskingCapability.NULLABLE.omittable)
        assertFalse(MaskingCapability.TEXTUAL_REQUIRED.omittable)
        assertFalse(MaskingCapability.UNSAFE.omittable)
    }

    @Test
    fun `redactDecision renders a placeholder for text, null for nullable, and fails closed for unsafe`() {
        assertEquals(MaskingDecision.WriteString("***"), MaskingCapability.TEXTUAL_REQUIRED.redactDecision("***"))
        assertEquals(MaskingDecision.WriteString("***"), MaskingCapability.TEXTUAL_OPTIONAL.redactDecision("***"))
        assertEquals(MaskingDecision.WriteNull, MaskingCapability.NULLABLE.redactDecision("***"))
        assertEquals(MaskingDecision.Omit(FailReason.REDACT_UNSAFE), MaskingCapability.UNSAFE.redactDecision("***"))
    }

    @Test
    fun `unenforceableReason flags HIDE on a required field and REDACT on an unsafe field`() {
        // HIDE — valid only where the field may be absent.
        assertNull(MaskingCapability.TEXTUAL_OPTIONAL.unenforceableReason(MaskingStrategy.HIDE))
        assertNull(MaskingCapability.NULLABLE.unenforceableReason(MaskingStrategy.HIDE))
        assertEquals(FailReason.HIDE_NON_NULLABLE, MaskingCapability.TEXTUAL_REQUIRED.unenforceableReason(MaskingStrategy.HIDE))
        assertEquals(FailReason.HIDE_NON_NULLABLE, MaskingCapability.UNSAFE.unenforceableReason(MaskingStrategy.HIDE))

        // REDACT — valid unless the field has no contract-safe masked value.
        assertNull(MaskingCapability.TEXTUAL_REQUIRED.unenforceableReason(MaskingStrategy.REDACT))
        assertNull(MaskingCapability.NULLABLE.unenforceableReason(MaskingStrategy.REDACT))
        assertEquals(FailReason.REDACT_UNSAFE, MaskingCapability.UNSAFE.unenforceableReason(MaskingStrategy.REDACT))
    }
}
