package org.ihawu.kotlinx

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.serialization.json.Json
import org.ihawu.core.masking.DefaultMaskingEngine
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.jackson.IhawuModule
import org.ihawu.jackson.IhawuSerialization
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cross-backend smoke test: the Jackson and kotlinx backends mask the same payload the same way. The
 * timing/allocation harness that used to live here was replaced by the JMH `benchmark` module (#102);
 * authoritative numbers are recorded in ADR 0008.
 */
class BackendBenchmarkTest {
    @Test
    fun bothBackendsMaskTheSamePayload() {
        val resolver = TestResolver()
        val principal = IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())

        val mapper = ObjectMapper().registerKotlinModule().registerModule(IhawuModule(resolver))
        val jackson = mapper.writer().withAttribute(IhawuSerialization.PRINCIPAL, principal).writeValueAsString(sample)

        val engine = DefaultMaskingEngine(resolver)
        val registry = maskingRegistry(Employee.serializer() to "employee", Address.serializer() to "address")
        val serializer = maskingSerializer(Employee.serializer(), engine, registry)
        val kotlinx = IhawuKotlinxJson.encodeToString(Json, principal, serializer, sample)

        for (out in listOf(jackson, kotlinx)) {
            assertTrue(out.contains("\"ssn\":\"***\""), "ssn must be redacted: $out")
            assertTrue(!out.contains("salary"), "salary must be hidden: $out")
            assertTrue(!out.contains("123-45-6789"), "raw SSN must not appear: $out")
        }
    }
}
