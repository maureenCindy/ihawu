package org.ihawu.jackson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.ihawu.core.exception.IhawuCoreException
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.ResourcePolicy
import org.ihawu.core.policy.RoleBasedResourcePolicyResolver
import java.io.InputStream

/**
 * Loads a [RoleBasedResourcePolicyResolver] from JSON configuration.
 *
 * The configuration is a JSON object keyed by resource name; each resource maps role names to an array
 * of field policies. `placeholder` is optional. Parsing is eager and fail-fast — an invalid document or
 * rule throws [IhawuCoreException] at load time, never at request time:
 *
 * ```json
 * {
 *   "employee": {
 *     "MANAGER": [ { "field": "salary", "strategy": "REDACT", "placeholder": "***" } ],
 *     "AUDITOR": [ { "field": "ssn", "strategy": "HIDE" } ]
 *   }
 * }
 * ```
 *
 * This lives in `ihawu-jackson` rather than `ihawu-core` so the core stays free of a serialization
 * dependency; the parsed [ResourcePolicy] rules it produces are plain core types.
 *
 * @sample org.ihawu.samples.policy.loadResolverFromJson
 */
object JacksonPolicyConfig {
    private val mapper = ObjectMapper()

    /**
     * Builds a resolver from a JSON configuration [String]. See the class documentation for the schema.
     * Parsing is eager and fail-fast.
     *
     * @throws IhawuCoreException if the JSON is invalid or any rule is malformed.
     */
    fun fromJson(json: String): RoleBasedResourcePolicyResolver = fromTree(readTree { mapper.readTree(json) })

    /**
     * Builds a resolver from a JSON configuration [InputStream] (e.g. a classpath resource). See the
     * class documentation for the schema. Parsing is eager and fail-fast.
     *
     * @throws IhawuCoreException if the JSON is invalid or any rule is malformed.
     */
    fun fromJson(input: InputStream): RoleBasedResourcePolicyResolver = fromTree(readTree { mapper.readTree(input) })

    private inline fun readTree(read: () -> JsonNode?): JsonNode =
        try {
            // Defensive: current Jackson returns a MissingNode (caught by the isObject check
            // in fromTree), not null, for empty input — so this guards a contract that could
            // change rather than a path reachable today.
            read() ?: throw IhawuCoreException("Policy configuration is empty")
        } catch (e: IhawuCoreException) {
            throw e
        } catch (e: Exception) {
            throw IhawuCoreException("Policy configuration is not valid JSON", e)
        }

    private fun fromTree(root: JsonNode): RoleBasedResourcePolicyResolver {
        if (!root.isObject) {
            throw IhawuCoreException("Policy configuration must be a JSON object of resource -> role -> policies")
        }
        val policies =
            root
                .properties()
                .map { (resource, roles) ->
                    if (!roles.isObject) {
                        throw IhawuCoreException("Resource '$resource' must map role names to field policies")
                    }
                    val roleFieldPolicies =
                        roles.properties().associate { (role, fields) ->
                            if (!fields.isArray) {
                                throw IhawuCoreException("Role '$role' in resource '$resource' must be an array of field policies")
                            }
                            role to fields.map { fieldPolicy(it, resource, role) }
                        }
                    ResourcePolicy(resource, roleFieldPolicies)
                }.toList()
        return RoleBasedResourcePolicyResolver(policies)
    }

    private fun fieldPolicy(
        node: JsonNode,
        resource: String,
        role: String,
    ): FieldPolicy {
        val field =
            node.get("field")?.takeIf { it.isTextual }?.asText()
                ?: throw IhawuCoreException("A field policy for role '$role' in resource '$resource' is missing a textual 'field'")
        val strategyName =
            node.get("strategy")?.takeIf { it.isTextual }?.asText()
                ?: throw IhawuCoreException("Field '$field' (role '$role', resource '$resource') is missing a textual 'strategy'")
        val strategy =
            runCatching { MaskingStrategy.valueOf(strategyName) }.getOrElse {
                throw IhawuCoreException(
                    "Field '$field' (role '$role', resource '$resource') has unknown strategy '$strategyName'; " +
                        "expected one of ${MaskingStrategy.entries.joinToString { it.name }}",
                )
            }
        val placeholder = node.get("placeholder")?.takeIf { it.isTextual }?.asText()
        return FieldPolicy(field, strategy, placeholder)
    }
}
