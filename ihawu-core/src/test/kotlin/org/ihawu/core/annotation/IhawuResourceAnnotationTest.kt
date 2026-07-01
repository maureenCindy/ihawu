package org.ihawu.core.annotation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@IhawuResource(name = "employee")
private class FixtureDto

class IhawuResourceAnnotationTest {
    @Test
    fun `is retained at runtime with its name`() {
        val annotation =
            assertNotNull(
                FixtureDto::class.java.getAnnotation(IhawuResource::class.java),
                "missing @IhawuResource — @Retention is not RUNTIME",
            )
        assertEquals("employee", annotation.name)
    }
}
