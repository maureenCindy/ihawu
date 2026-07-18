package org.ihawu.kotlinx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ihawu.core.masking.DefaultMaskingEngine
import org.ihawu.core.policy.IhawuPrincipal
import kotlin.test.Test
import kotlin.test.assertEquals

/** Proves the coroutine->thread-local bridge (`maskingContextElement`) — the mechanism Ktor (#82) uses. */
class CoroutineBridgeTest {
    @Test
    fun masksAcrossACoroutineDispatcherSwitch() =
        runBlocking {
            val json = Json
            val engine = DefaultMaskingEngine(TestResolver())
            val registry = maskingRegistry(Employee.serializer() to "employee", Address.serializer() to "address")
            val serializer = maskingSerializer(Employee.serializer(), engine, registry)
            val principal = IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())

            // Encode on a Default-dispatcher thread; the context rides along via the context element.
            val out =
                withContext(Dispatchers.Default + maskingContextElement(principal)) {
                    json.parseToJsonElement(json.encodeToString(serializer, sample)).jsonObject
                }

            assertEquals("***", out["ssn"]?.jsonPrimitive?.content) // masked despite the thread switch
        }
}
