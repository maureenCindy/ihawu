package org.ihawu.samples.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.exception.IhawuCoreException
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicyResolver
import org.ihawu.core.serialization.IhawuModule
import org.ihawu.core.serialization.IhawuSerialization

@IhawuResource("employee.address")
data class Address(
    val city: String,
    val postalCode: String,
)

@IhawuResource("employee.profile")
data class EmployeeProfile(
    val name: String,
    val ssn: String,
    val salary: Int,
    val address: Address,
)

/** A resolver backed by static `resource -> policies` rules. */
class StaticPolicyResolver(
    private val rules: Map<String, List<FieldPolicy>>,
) : ResourcePolicyResolver {
    override fun resolve(
        principal: IhawuPrincipal,
        resource: String,
    ): List<FieldPolicy> = rules[resource] ?: emptyList()
}

fun maskEmployeeProfile() {
    // HIDE omits a field, REDACT replaces its value, unlisted fields pass through, and
    // nested @IhawuResource objects are masked by their own policy.

    // A resolver supplies the field policies for each resource.
    val resolver =
        StaticPolicyResolver(
            mapOf(
                "employee.profile" to
                    listOf(
                        FieldPolicy("ssn", MaskingStrategy.HIDE),
                        FieldPolicy("salary", MaskingStrategy.REDACT, placeholder = "REDACTED"),
                    ),
                "employee.address" to
                    listOf(FieldPolicy("postalCode", MaskingStrategy.HIDE)),
            ),
        )

    // Register Ihawu on the application's ObjectMapper.
    val mapper = ObjectMapper().registerModule(IhawuModule(resolver))

    // The caller's identity is attached to this single serialization call.
    val principal = IhawuPrincipal("u1", roles = setOf("MANAGER"), attributes = emptyMap())
    val profile =
        EmployeeProfile(
            name = "Jane Doe",
            ssn = "123-45-6789",
            salary = 90_000,
            address = Address(city = "Harare", postalCode = "00263"),
        )

    val json =
        mapper
            .writer()
            .withAttribute(IhawuSerialization.PRINCIPAL, principal)
            .writeValueAsString(profile)

    val tree = mapper.readTree(json)
    check(!tree.has("ssn")) // HIDE -> field omitted
    check(tree["salary"].asText() == "REDACTED") // REDACT -> placeholder
    check(tree["name"].asText() == "Jane Doe") // not in policy -> unchanged
    check(!tree["address"].has("postalCode")) // nested resource masked by its own policy
    check(tree["address"]["city"].asText() == "Harare")
}

fun maskCollectionItems() {
    // Every item in a collection of resources receives the same field masking.
    val resolver =
        StaticPolicyResolver(
            mapOf("employee.profile" to listOf(FieldPolicy("ssn", MaskingStrategy.HIDE))),
        )
    val mapper = ObjectMapper().registerModule(IhawuModule(resolver))
    val principal = IhawuPrincipal("u1", roles = setOf("MANAGER"), attributes = emptyMap())

    val profiles =
        listOf(
            EmployeeProfile("Jane Doe", "123-45-6789", 90_000, Address("Harare", "00263")),
            EmployeeProfile("John Roe", "987-65-4321", 80_000, Address("Bulawayo", "00264")),
        )

    val json =
        mapper
            .writer()
            .withAttribute(IhawuSerialization.PRINCIPAL, principal)
            .writeValueAsString(profiles)

    val tree = mapper.readTree(json)

    check(tree.all { !it.has("ssn") }) // every item masked
}

fun maskFailsClosedWithoutPrincipal() {
    // With no principal attached to the call, the serializer fails closed and emits an empty object.
    val resolver =
        StaticPolicyResolver(
            mapOf("employee.profile" to listOf(FieldPolicy("ssn", MaskingStrategy.HIDE))),
        )
    val mapper = ObjectMapper().registerModule(IhawuModule(resolver))

    val profile = EmployeeProfile("Jane Doe", "123-45-6789", 90_000, Address("Harare", "00263"))

    // No IhawuSerialization.PRINCIPAL attribute set on the writer.
    val json = mapper.writeValueAsString(profile)

    check(mapper.readTree(json).isEmpty) // {} -> nothing leaked
}

fun failClosedOnResolverError() {
    // If the resolver throws — a misconfiguration or a policy-store outage — Ihawu fails closed:
    // the protected resource serializes as an empty object instead of leaking unmasked data.
    val failingResolver =
        object : ResourcePolicyResolver {
            override fun resolve(
                principal: IhawuPrincipal,
                resource: String,
            ): List<FieldPolicy> = throw IhawuCoreException("policy store unavailable")
        }
    val mapper = ObjectMapper().registerModule(IhawuModule(failingResolver))
    val principal = IhawuPrincipal("u1", roles = setOf("MANAGER"), attributes = emptyMap())

    val profile = EmployeeProfile("Jane Doe", "123-45-6789", 90_000, Address("Harare", "00263"))

    val json =
        mapper
            .writer()
            .withAttribute(IhawuSerialization.PRINCIPAL, principal)
            .writeValueAsString(profile)

    check(mapper.readTree(json).isEmpty) // {} -> a resolver failure never leaks data
}
