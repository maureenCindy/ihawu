package org.ihawu.ktor

import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.reflect.TypeInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.masking.DefaultMaskingEngine
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicy
import org.ihawu.core.policy.RoleBasedResourcePolicyResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@Serializable
@IhawuResource("employee")
private data class Employee(
    val name: String,
    val salary: Int,
    val ssn: String,
)

@Serializable
private data class Health(
    val status: String,
)

private val sample = Employee(name = "Ada", salary = 100, ssn = "123-45-6789")

private val employeePolicy =
    ResourcePolicy(
        "employee",
        mapOf(
            "MANAGER" to listOf(FieldPolicy("ssn", MaskingStrategy.REDACT, "***")),
            "EMPLOYEE" to listOf(FieldPolicy("salary", MaskingStrategy.HIDE), FieldPolicy("ssn", MaskingStrategy.HIDE)),
        ),
    )

/** Resolves the caller from an `X-Role` header — a test stand-in for a real `Authentication` bridge. */
private fun Application.installByRoleHeader(configure: IhawuKtorConfig.() -> Unit = {}) {
    install(IhawuKtor) {
        resolvePrincipal = { call ->
            when (call.request.headers["X-Role"]) {
                "MANAGER" -> IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())
                "EMPLOYEE" -> IhawuPrincipal("u2", setOf("EMPLOYEE"), emptyMap())
                else -> null
            }
        }
        resources(Employee.serializer() to "employee")
        configure()
    }
    routing {
        get("/employee") { call.respond(sample) }
        get("/health") { call.respond(Health("ok")) }
        post("/echo") {
            val received = call.receive<Employee>()
            call.respondText(received.name)
        }
    }
}

private fun parse(body: String) = Json.parseToJsonElement(body).jsonObject

class IhawuKtorTest {
    @Test
    fun managerSeesRedactedSsnAndVisibleSalary() =
        testApplication {
            application { installByRoleHeader { policies(employeePolicy) } }

            val body = client.get("/employee") { header("X-Role", "MANAGER") }.bodyAsText()
            val json = parse(body)

            assertEquals("Ada", json["name"]?.jsonPrimitive?.content)
            assertEquals("100", json["salary"]?.jsonPrimitive?.content)
            assertEquals("***", json["ssn"]?.jsonPrimitive?.content)
        }

    @Test
    fun employeeHasSalaryAndSsnHidden() =
        testApplication {
            application { installByRoleHeader { policies(employeePolicy) } }

            val json = parse(client.get("/employee") { header("X-Role", "EMPLOYEE") }.bodyAsText())

            assertEquals("Ada", json["name"]?.jsonPrimitive?.content)
            assertFalse("salary" in json, "salary must be hidden for EMPLOYEE")
            assertFalse("ssn" in json, "ssn must be hidden for EMPLOYEE")
        }

    @Test
    fun noPrincipalFailsClosedToEmptyObject() =
        testApplication {
            application { installByRoleHeader { policies(employeePolicy) } }

            val body = client.get("/employee").bodyAsText()

            assertEquals("{}", body.filterNot { it.isWhitespace() })
        }

    @Test
    fun nonResourceTypePassesThroughUnmasked() =
        testApplication {
            application { installByRoleHeader { policies(employeePolicy) } }

            // No principal, yet a non-resource is untouched: the registry masks resources only.
            val json = parse(client.get("/health").bodyAsText())

            assertEquals("ok", json["status"]?.jsonPrimitive?.content)
        }

    @Test
    fun customPolicyResolverIsUsed() =
        testApplication {
            application {
                installByRoleHeader { policyResolver(RoleBasedResourcePolicyResolver(listOf(employeePolicy))) }
            }

            val json = parse(client.get("/employee") { header("X-Role", "MANAGER") }.bodyAsText())

            assertEquals("***", json["ssn"]?.jsonPrimitive?.content)
        }

    @Test
    fun defaultResolverGrantsVisibilityWhenNoPolicyConfigured() =
        testApplication {
            // No policies()/policyResolver() call: the plugin falls back to an empty resolver, so a
            // matched principal with no policy sees every field (ADR 0003 default visibility).
            application { installByRoleHeader() }

            val json = parse(client.get("/employee") { header("X-Role", "MANAGER") }.bodyAsText())

            assertEquals("100", json["salary"]?.jsonPrimitive?.content)
            assertEquals("123-45-6789", json["ssn"]?.jsonPrimitive?.content)
        }

    @Test
    fun deserializeReadsRequestBody() =
        testApplication {
            application { installByRoleHeader { policies(employeePolicy) } }

            val response =
                client.post("/echo") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Grace","salary":200,"ssn":"999-99-9999"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Grace", response.bodyAsText())
        }

    @Test
    fun ihawuPrincipalBridgeMasksAuthenticatedCallAndFailsClosedOtherwise() =
        testApplication {
            application {
                install(Authentication) {
                    basic("mgr") {
                        validate { cred ->
                            if (cred.name == "mgr") IhawuPrincipal("u1", setOf("MANAGER"), emptyMap()) else null
                        }
                    }
                }
                install(IhawuKtor) {
                    resolvePrincipal = { it.ihawuPrincipal() }
                    policies(employeePolicy)
                    resources(Employee.serializer() to "employee")
                }
                routing {
                    authenticate("mgr", optional = true) {
                        get("/employee") { call.respond(sample) }
                    }
                }
            }

            val authed =
                parse(client.get("/employee") { basicAuth("mgr", "pw") }.bodyAsText())
            assertEquals("***", authed["ssn"]?.jsonPrimitive?.content)

            val anon = client.get("/employee").bodyAsText()
            assertEquals("{}", anon.filterNot { it.isWhitespace() })
        }

    @Test
    fun serializeRequiresAKotlinType() {
        val converter =
            MaskingContentConverter(
                Json,
                DefaultMaskingEngine(RoleBasedResourcePolicyResolver(emptyList())),
                emptyMap(),
            )

        // A TypeInfo with no kotlinType (the type is erased) cannot resolve a serializer — fail loudly.
        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                converter.serialize(ContentType.Application.Json, Charsets.UTF_8, TypeInfo(Employee::class), sample)
            }
        }
    }
}
