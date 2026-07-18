package org.ihawu.samples.ktor

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicy
import org.ihawu.ktor.IhawuKtor
import org.ihawu.ktor.ihawuPrincipal

/** A masked response DTO: `salary` and `ssn` are sensitive; the policy decides who sees them. */
@Serializable
@IhawuResource("employee")
data class Employee(
    val name: String,
    val salary: Int,
    val ssn: String,
)

// --- Polymorphism (0.4.0) ------------------------------------------------------------------------
// Ihawu masks polymorphic @IhawuResource responses too: the concrete subtype is masked and the class
// discriminator ("type") is preserved. Sealed hierarchies work out of the box; OPEN (non-sealed)
// hierarchies additionally require the app to register their subtypes on the Json's SerializersModule.

/** A SEALED `@IhawuResource`: the subtype is masked, discriminator preserved. No module needed. */
@Serializable
@IhawuResource("payment")
sealed interface Payment {
    @Serializable
    @SerialName("card")
    data class Card(
        val holder: String,
        val pan: String, // REDACT
    ) : Payment

    @Serializable
    @SerialName("bank")
    data class Bank(
        val holder: String,
        val iban: String, // REDACT
    ) : Payment
}

/** An OPEN (non-sealed abstract) `@IhawuResource`: its subtypes live on the [appJson] module (below). */
@Serializable
@IhawuResource("alert")
abstract class Alert {
    abstract val detail: String
}

@Serializable
@SerialName("fraud")
data class FraudAlert(
    override val detail: String, // REDACT
) : Alert()

private val sampleEmployee = Employee(name = "Ada Lovelace", salary = 145_000, ssn = "123-45-6789")
private val samplePayment: Payment = Payment.Card(holder = "Ada Lovelace", pan = "4111 1111 1111 1111")
private val sampleAlert: Alert = FraudAlert(detail = "card ending 1111 used in two countries within a minute")

/**
 * The app's Json, with the OPEN [Alert] subtype registered on its module — required for OPEN masking
 * (a sealed hierarchy needs none). The plugin uses this Json to encode responses.
 */
private val appJson =
    Json {
        serializersModule =
            SerializersModule {
                polymorphic(Alert::class) { subclass(FraudAlert::class) }
            }
    }

/** A tiny in-memory user directory mapping basic credentials to an [IhawuPrincipal]. */
private data class User(
    val password: String,
    val principal: IhawuPrincipal,
)

private val users =
    mapOf(
        "manager" to User("secret", IhawuPrincipal("u-manager", setOf("MANAGER"), emptyMap())),
        "employee" to User("secret", IhawuPrincipal("u-employee", setOf("EMPLOYEE"), emptyMap())),
    )

/**
 * The Ktor module. `install(IhawuKtor)` masks every `@IhawuResource` response per the caller's role:
 * a MANAGER sees the SSN redacted; an EMPLOYEE has salary and SSN hidden; an unauthenticated caller
 * fails closed to `{}`. It also masks the polymorphic `/payment` (sealed) and `/alert` (OPEN) responses.
 */
fun Application.module() {
    install(Authentication) {
        basic("auth") {
            validate { credentials ->
                users[credentials.name]?.takeIf { it.password == credentials.password }?.principal
            }
        }
    }

    install(IhawuKtor) {
        json = appJson // carries the OPEN Alert subtype registration
        resolvePrincipal = { it.ihawuPrincipal() }
        policies(
            ResourcePolicy(
                "employee",
                mapOf(
                    "MANAGER" to listOf(FieldPolicy("ssn", MaskingStrategy.REDACT, "***-**-****")),
                    "EMPLOYEE" to
                        listOf(
                            FieldPolicy("salary", MaskingStrategy.HIDE),
                            FieldPolicy("ssn", MaskingStrategy.HIDE),
                        ),
                ),
            ),
            ResourcePolicy(
                "payment",
                mapOf(
                    "MANAGER" to
                        listOf(
                            FieldPolicy("pan", MaskingStrategy.REDACT, "**** **** **** ****"),
                            FieldPolicy("iban", MaskingStrategy.REDACT, "REDACTED"),
                        ),
                ),
            ),
            ResourcePolicy(
                "alert",
                mapOf("MANAGER" to listOf(FieldPolicy("detail", MaskingStrategy.REDACT, "[redacted]"))),
            ),
        )
        resources(
            Employee.serializer() to "employee",
            // Register each polymorphic subtype serializer under its resource name.
            Payment.Card.serializer() to "payment",
            Payment.Bank.serializer() to "payment",
            FraudAlert.serializer() to "alert",
        )
    }

    routing {
        // optional = true so an unauthenticated caller still reaches the route and fails closed to {}.
        authenticate("auth", optional = true) {
            get("/employee") { call.respond(sampleEmployee) }
            get("/payment") { call.respond(samplePayment) } // sealed @IhawuResource
            get("/alert") { call.respond(sampleAlert) } // OPEN @IhawuResource
        }
    }
}

fun main() {
    embeddedServer(Netty, port = 8080) { module() }.start(wait = true)
}
