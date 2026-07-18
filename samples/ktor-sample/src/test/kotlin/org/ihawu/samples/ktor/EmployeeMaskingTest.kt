package org.ihawu.samples.ktor

import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Pins the masked JSON per role — the integration test the sample exists to demonstrate. */
class EmployeeMaskingTest {
    private fun body(block: suspend (io.ktor.client.HttpClient) -> String): String {
        var out = ""
        testApplication {
            application { module() }
            out = block(client)
        }
        return out
    }

    private fun json(body: String) = Json.parseToJsonElement(body).jsonObject

    @Test
    fun managerSeesRedactedSsn() {
        val response = json(body { it.get("/employee") { basicAuth("manager", "secret") }.bodyAsText() })

        assertEquals("Ada Lovelace", response["name"]?.jsonPrimitive?.content)
        assertEquals("145000", response["salary"]?.jsonPrimitive?.content)
        assertEquals("***-**-****", response["ssn"]?.jsonPrimitive?.content)
    }

    @Test
    fun employeeHasSalaryAndSsnHidden() {
        val response = json(body { it.get("/employee") { basicAuth("employee", "secret") }.bodyAsText() })

        assertEquals("Ada Lovelace", response["name"]?.jsonPrimitive?.content)
        assertFalse("salary" in response, "salary must be hidden for an EMPLOYEE")
        assertFalse("ssn" in response, "ssn must be hidden for an EMPLOYEE")
    }

    @Test
    fun unauthenticatedCallerFailsClosed() {
        val raw = body { it.get("/employee").bodyAsText() }

        assertEquals("{}", raw.filterNot { it.isWhitespace() })
    }
}
