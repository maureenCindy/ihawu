package org.ihawu.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.masking.DefaultMaskingEngine
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver
import org.ihawu.jackson.IhawuModule
import org.ihawu.jackson.IhawuSerialization
import org.ihawu.kotlinx.IhawuKotlinxJson
import org.ihawu.kotlinx.maskingRegistry
import org.ihawu.kotlinx.maskingSerializer
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

/** The representative masked resource: fields by strategy/type, a nested resource, a map of resources. */
@Serializable
@IhawuResource("employee")
data class Employee(
    val id: String,
    val name: String,
    val ssn: String?, // REDACT -> "***"
    val salary: Int?, // HIDE -> dropped
    val bonus: Int?, // REDACT -> null (nullable non-String)
    val address: Address?, // nested resource
    val reports: Map<String, Address> = emptyMap(), // map of resources
)

@Serializable
@IhawuResource("address")
data class Address(
    val city: String,
    val zip: String?, // HIDE -> dropped
)

private class BenchResolver : ResourcePolicyResolver {
    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> =
        when (resource) {
            "employee" ->
                listOf(
                    FieldPolicy("ssn", MaskingStrategy.REDACT, "***"),
                    FieldPolicy("salary", MaskingStrategy.HIDE),
                    FieldPolicy("bonus", MaskingStrategy.REDACT),
                )
            "address" -> listOf(FieldPolicy("zip", MaskingStrategy.HIDE))
            else -> emptyList()
        }
}

/**
 * Compares the masking backends against their plain-serialization baselines (issue #102, ADR 0008):
 * `ihawuJackson` (streaming writer-wrapping) and `ihawuKotlinx` (JsonElement rewrite) vs `plainJackson`
 * and `plainKotlinx`. The plain baselines answer the headline question — what masking costs over plain
 * serialization — and the cross-backend pair replaces the interim nanoTime harness's directional numbers.
 *
 * Run with `./gradlew :benchmark:jmh` (throughput + gc.alloc.rate.norm B/op; see README.md).
 */
@State(Scope.Benchmark)
class MaskingBenchmark {
    /** `small` = one employee (nested resource + map); `large` = a list of 100 of them. */
    @Param("small", "large")
    var size: String = "small"

    private lateinit var plainJacksonOp: () -> String
    private lateinit var ihawuJacksonOp: () -> String
    private lateinit var plainKotlinxOp: () -> String
    private lateinit var ihawuKotlinxOp: () -> String

    @Setup
    fun setup() {
        val employee =
            Employee(
                id = "e1",
                name = "Ada Lovelace",
                ssn = "123-45-6789",
                salary = 145_000,
                bonus = 5_000,
                address = Address("Harare", "00263"),
                reports = mapOf("r1" to Address("Bulawayo", "00264"), "r2" to Address("Gweru", "00265")),
            )
        val large = List(100) { employee.copy(id = "e$it") }

        val resolver = BenchResolver()
        val principal = IhawuPrincipal("u1", setOf("MANAGER"), emptyMap())

        val plainMapper = ObjectMapper().registerKotlinModule()
        val ihawuWriter =
            ObjectMapper()
                .registerKotlinModule()
                .registerModule(IhawuModule(resolver))
                .writer()
                .withAttribute(IhawuSerialization.PRINCIPAL, principal)

        val json = Json
        val engine = DefaultMaskingEngine(resolver)
        val registry = maskingRegistry(Employee.serializer() to "employee", Address.serializer() to "address")
        val one = Employee.serializer()
        val many = ListSerializer(Employee.serializer())
        val maskedOne = maskingSerializer(one, engine, registry)
        val maskedMany = maskingSerializer(many, engine, registry)

        when (size) {
            "small" -> {
                plainJacksonOp = { plainMapper.writeValueAsString(employee) }
                ihawuJacksonOp = { ihawuWriter.writeValueAsString(employee) }
                plainKotlinxOp = { json.encodeToString(one, employee) }
                ihawuKotlinxOp = { IhawuKotlinxJson.encodeToString(json, principal, maskedOne, employee) }
            }
            else -> {
                plainJacksonOp = { plainMapper.writeValueAsString(large) }
                ihawuJacksonOp = { ihawuWriter.writeValueAsString(large) }
                plainKotlinxOp = { json.encodeToString(many, large) }
                ihawuKotlinxOp = { IhawuKotlinxJson.encodeToString(json, principal, maskedMany, large) }
            }
        }

        // Sanity: both masked paths actually mask, both plain paths do not.
        check(ihawuJacksonOp().contains("***") && ihawuKotlinxOp().contains("***"))
        check(plainJacksonOp().contains("123-45-6789") && plainKotlinxOp().contains("123-45-6789"))
    }

    @Benchmark fun plainJackson(): String = plainJacksonOp()

    @Benchmark fun ihawuJackson(): String = ihawuJacksonOp()

    @Benchmark fun plainKotlinx(): String = plainKotlinxOp()

    @Benchmark fun ihawuKotlinx(): String = ihawuKotlinxOp()
}
