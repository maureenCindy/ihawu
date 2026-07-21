package org.ihawu.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.serialization.Serializable
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.masking.FailReason
import org.ihawu.core.masking.MaskingFailure
import org.ihawu.core.masking.MaskingFailureSink
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
@IhawuResource("account")
private data class Account(
    val id: String,
    val balance: Int,
)

class ObservabilitySinksTest {
    @Test
    fun `micrometer sink counts failures tagged by resource and reason`() {
        val registry = SimpleMeterRegistry()
        val sink = MicrometerMaskingFailureSink(registry)

        sink.onFailClosed(MaskingFailure("employee", FailReason.RESOLVER_ERROR, cause = RuntimeException("down")))
        sink.onFailClosed(MaskingFailure("employee", FailReason.RESOLVER_ERROR, cause = RuntimeException("down")))
        sink.onFailClosed(MaskingFailure("account", FailReason.HIDE_NON_NULLABLE, field = "ssn"))

        assertEquals(
            2.0,
            registry.counter("ihawu.masking.failures", "resource", "employee", "reason", "RESOLVER_ERROR").count(),
        )
        assertEquals(
            1.0,
            registry.counter("ihawu.masking.failures", "resource", "account", "reason", "HIDE_NON_NULLABLE").count(),
        )
    }

    @Test
    fun `composite sink fans out to every delegate in order`() {
        val calls = mutableListOf<String>()
        val a = MaskingFailureSink { failure -> calls += "a:${failure.resource}" }
        val b = MaskingFailureSink { failure -> calls += "b:${failure.resource}" }

        CompositeMaskingFailureSink(a, b).onFailClosed(MaskingFailure("account", FailReason.NO_PRINCIPAL))

        assertEquals(listOf("a:account", "b:account"), calls)
    }

    @Test
    fun `the plugin routes fail-closed drops to the configured metrics sink`() =
        testApplication {
            val registry = SimpleMeterRegistry()
            application {
                install(IhawuKtor) {
                    // No resolvePrincipal → every response fails closed with NO_PRINCIPAL.
                    onFailClosed = MicrometerMaskingFailureSink(registry)
                    resources(Account.serializer() to "account")
                }
                routing { get("/account") { call.respond(Account("a1", 100)) } }
            }

            assertEquals("{}", client.get("/account").bodyAsText().filterNot { it.isWhitespace() })
            assertEquals(
                1.0,
                registry.counter("ihawu.masking.failures", "resource", "account", "reason", "NO_PRINCIPAL").count(),
            )
        }
}
