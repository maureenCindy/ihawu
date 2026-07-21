package org.ihawu.core.masking

import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Map-backed [MaskingContext] — memoizes per key exactly like the Jackson adapter, no Jackson. */
private class FakeContext(
    override val principal: IhawuPrincipal?,
) : MaskingContext {
    private val store = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> memoize(
        key: String,
        compute: () -> T,
    ): T = store.getOrPut(key, compute) as T
}

private class RecordingSink : MaskingFailureSink {
    data class Event(
        val resource: String,
        val field: String?,
        val reason: FailReason,
        val cause: Throwable?,
    )

    val events = mutableListOf<Event>()

    override fun onFailClosed(failure: MaskingFailure) {
        events += Event(failure.resource, failure.field, failure.reason, failure.cause)
    }
}

private class StubResolver(
    private val policies: List<FieldPolicy> = emptyList(),
    private val fail: Throwable? = null,
) : ResourcePolicyResolver {
    var calls = 0

    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> {
        calls++
        fail?.let { throw it }
        return policies
    }
}

class DefaultMaskingEngineTest {
    private val principal = IhawuPrincipal("u1", roles = setOf("MANAGER"), attributes = emptyMap())
    private val sink = RecordingSink()

    private fun engine(resolver: ResourcePolicyResolver) = DefaultMaskingEngine(resolver, sink)

    private fun decide(
        resolver: ResourcePolicyResolver,
        field: String,
        capability: MaskingCapability,
        principal: IhawuPrincipal? = this.principal,
    ) = engine(resolver).decide("employee", field, capability, FakeContext(principal))

    @Test
    fun `passes a field no policy restricts`() {
        val decision = decide(StubResolver(), "name", MaskingCapability.TEXTUAL_REQUIRED)

        assertEquals(MaskingDecision.Pass, decision)
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `REDACT on a String writes the placeholder`() {
        val resolver = StubResolver(listOf(FieldPolicy("ssn", MaskingStrategy.REDACT, "***ssn")))

        assertEquals(MaskingDecision.WriteString("***ssn"), decide(resolver, "ssn", MaskingCapability.TEXTUAL_REQUIRED))
        assertEquals(MaskingDecision.WriteString("***ssn"), decide(resolver, "ssn", MaskingCapability.TEXTUAL_OPTIONAL))
    }

    @Test
    fun `REDACT without a placeholder falls back to the strategy default`() {
        val resolver = StubResolver(listOf(FieldPolicy("ssn", MaskingStrategy.REDACT)))

        val decision = decide(resolver, "ssn", MaskingCapability.TEXTUAL_REQUIRED)

        assertEquals(MaskingDecision.WriteString("***-**-****"), decision)
    }

    @Test
    fun `REDACT on a nullable non-String writes null`() {
        val resolver = StubResolver(listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "ignored")))

        assertEquals(MaskingDecision.WriteNull, decide(resolver, "salary", MaskingCapability.NULLABLE))
    }

    @Test
    fun `REDACT on a non-nullable non-String fails closed`() {
        val resolver = StubResolver(listOf(FieldPolicy("salary", MaskingStrategy.REDACT, "x")))

        val decision = decide(resolver, "salary", MaskingCapability.UNSAFE)

        assertEquals(MaskingDecision.Omit(FailReason.REDACT_UNSAFE), decision)
        assertEquals(RecordingSink.Event("employee", "salary", FailReason.REDACT_UNSAFE, null), sink.events.single())
    }

    @Test
    fun `HIDE on a nullable field omits silently`() {
        val resolver = StubResolver(listOf(FieldPolicy("salary", MaskingStrategy.HIDE)))

        assertEquals(MaskingDecision.Omit(), decide(resolver, "salary", MaskingCapability.NULLABLE))
        assertEquals(MaskingDecision.Omit(), decide(resolver, "salary", MaskingCapability.TEXTUAL_OPTIONAL))
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `HIDE on a non-nullable field fails closed`() {
        val resolver = StubResolver(listOf(FieldPolicy("ssn", MaskingStrategy.HIDE)))

        assertEquals(MaskingDecision.Omit(FailReason.HIDE_NON_NULLABLE), decide(resolver, "ssn", MaskingCapability.TEXTUAL_REQUIRED))
        assertEquals(RecordingSink.Event("employee", "ssn", FailReason.HIDE_NON_NULLABLE, null), sink.events.single())
    }

    @Test
    fun `no principal masks the whole resource and notifies once`() {
        val decision =
            decide(
                StubResolver(listOf(FieldPolicy("ssn", MaskingStrategy.REDACT, "x"))),
                "ssn",
                MaskingCapability.TEXTUAL_REQUIRED,
                principal = null,
            )

        assertEquals(MaskingDecision.Omit(FailReason.NO_PRINCIPAL), decision)
        assertEquals(RecordingSink.Event("employee", null, FailReason.NO_PRINCIPAL, null), sink.events.single())
    }

    @Test
    fun `resolver failure masks the whole resource and carries the cause`() {
        val boom = IllegalStateException("policy store down")

        val decision = decide(StubResolver(fail = boom), "ssn", MaskingCapability.TEXTUAL_REQUIRED)

        val omit = assertIs<MaskingDecision.Omit>(decision)
        assertEquals(FailReason.RESOLVER_ERROR, omit.reason)
        assertSame(boom, sink.events.single().cause)
    }

    @Test
    fun `fail-request mode throws on resolver error after notifying the sink`() {
        val boom = IllegalStateException("policy store down")
        val engine = DefaultMaskingEngine(StubResolver(fail = boom), sink, ResolverErrorMode.FAIL_REQUEST)

        val thrown =
            assertFailsWith<MaskingResolverException> {
                engine.decide("employee", "ssn", MaskingCapability.TEXTUAL_REQUIRED, FakeContext(principal))
            }

        assertEquals("employee", thrown.resource)
        assertSame(boom, thrown.cause)
        // Strictly more observable than mask-all: the sink fired before the throw.
        assertEquals(RecordingSink.Event("employee", null, FailReason.RESOLVER_ERROR, boom), sink.events.single())
    }

    @Test
    fun `fail-request mode leaves the missing-principal path masking, not throwing`() {
        val engine = DefaultMaskingEngine(StubResolver(), sink, ResolverErrorMode.FAIL_REQUEST)

        // A missing caller is a normal authorization state, not an outage: still masks fail-closed.
        val decision = engine.decide("employee", "ssn", MaskingCapability.TEXTUAL_REQUIRED, FakeContext(null))

        assertEquals(MaskingDecision.Omit(FailReason.NO_PRINCIPAL), decision)
        assertEquals(FailReason.NO_PRINCIPAL, sink.events.single().reason)
    }

    @Test
    fun `resolves policy once per resource across many fields`() {
        val resolver = StubResolver(listOf(FieldPolicy("ssn", MaskingStrategy.REDACT, "x")))
        val engine = engine(resolver)
        val context = FakeContext(principal)

        engine.decide("employee", "ssn", MaskingCapability.TEXTUAL_REQUIRED, context)
        engine.decide("employee", "name", MaskingCapability.TEXTUAL_REQUIRED, context)
        engine.decide("employee", "salary", MaskingCapability.NULLABLE, context)

        assertEquals(1, resolver.calls)
    }
}
