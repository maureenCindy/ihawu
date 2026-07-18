package org.ihawu.kotlinx

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.serialization.json.Json
import org.ihawu.core.masking.DefaultMaskingEngine
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.jackson.IhawuModule
import org.ihawu.jackson.IhawuSerialization
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A rough throughput/allocation harness (NOT JMH) comparing the Jackson and kotlinx backends masking the
 * same payload. Good enough for a directional comparison and the ADR; a proper JMH module is a follow-up.
 * Also asserts both backends actually mask, so it doubles as a cross-backend smoke test.
 */
class BackendBenchmarkTest {
    @Test
    fun benchmarkJacksonVsKotlinx() {
        val resolver = TestResolver()
        val principal = IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())

        val mapper = ObjectMapper().registerKotlinModule().registerModule(IhawuModule(resolver))
        val jacksonWriter = mapper.writer().withAttribute(IhawuSerialization.PRINCIPAL, principal)
        val jackson: () -> String = { jacksonWriter.writeValueAsString(sample) }

        val json = Json
        val engine = DefaultMaskingEngine(resolver)
        val registry = maskingRegistry(Employee.serializer() to "employee", Address.serializer() to "address")
        val serializer = maskingSerializer(Employee.serializer(), engine, registry)
        val kotlinx: () -> String = { IhawuKotlinxJson.encodeToString(json, principal, serializer, sample) }

        assertTrue(jackson().contains("***") && kotlinx().contains("***")) // both mask

        val n = 50_000
        repeat(3) {
            val j = measure(n, jackson)
            val k = measure(n, kotlinx)
            println(
                "BENCH n=$n | jackson ${j.opsPerSec} ops/s, ${j.bytesPerOp} B/op | " +
                    "kotlinx ${k.opsPerSec} ops/s, ${k.bytesPerOp} B/op | " +
                    "throughput ratio ${"%.2f".format(j.opsPerSec.toDouble() / k.opsPerSec)}x, " +
                    "alloc ratio ${"%.2f".format(k.bytesPerOp.toDouble() / j.bytesPerOp)}x",
            )
        }
    }

    private data class Result(
        val opsPerSec: Long,
        val bytesPerOp: Long,
    )

    private fun measure(
        n: Int,
        op: () -> String,
    ): Result {
        repeat(n / 5) { op() } // warm up
        val allocBean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        val tid = Thread.currentThread().id
        val startBytes = allocBean?.getThreadAllocatedBytes(tid) ?: 0
        val startNs = System.nanoTime()
        repeat(n) { op() }
        val elapsedNs = System.nanoTime() - startNs
        val bytes = (allocBean?.getThreadAllocatedBytes(tid) ?: 0) - startBytes
        return Result(
            opsPerSec = n * 1_000_000_000L / elapsedNs,
            bytesPerOp = if (bytes > 0) bytes / n else 0,
        )
    }
}
