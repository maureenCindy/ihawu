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
import kotlinx.serialization.Serializable
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

private val sampleEmployee = Employee(name = "Ada Lovelace", salary = 145_000, ssn = "123-45-6789")

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
 * fails closed to `{}`.
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
        )
        resources(Employee.serializer() to "employee")
    }

    routing {
        // optional = true so an unauthenticated caller still reaches the route and fails closed to {}.
        authenticate("auth", optional = true) {
            get("/employee") { call.respond(sampleEmployee) }
        }
    }
}

fun main() {
    embeddedServer(Netty, port = 8080) { module() }.start(wait = true)
}
