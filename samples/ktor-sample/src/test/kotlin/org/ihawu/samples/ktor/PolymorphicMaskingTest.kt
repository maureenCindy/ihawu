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

/** Pins the masked JSON for the polymorphic (0.4.0) endpoints — sealed `/payment` and OPEN `/alert`. */
class PolymorphicMaskingTest {
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
    fun sealedSubtypeMasksAndKeepsTheDiscriminator() {
        val response = json(body { it.get("/payment") { basicAuth("manager", "secret") }.bodyAsText() })

        assertEquals("card", response["type"]?.jsonPrimitive?.content) // discriminator preserved
        assertEquals("Ada Lovelace", response["holder"]?.jsonPrimitive?.content)
        assertEquals("**** **** **** ****", response["pan"]?.jsonPrimitive?.content) // masked
    }

    @Test
    fun openSubtypeMasksThroughTheRegisteredModule() {
        val response = json(body { it.get("/alert") { basicAuth("manager", "secret") }.bodyAsText() })

        assertEquals("fraud", response["type"]?.jsonPrimitive?.content) // discriminator preserved
        assertEquals("[redacted]", response["detail"]?.jsonPrimitive?.content) // OPEN subtype masked
    }
}
