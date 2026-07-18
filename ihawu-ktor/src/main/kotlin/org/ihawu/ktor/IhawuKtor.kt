package org.ihawu.ktor

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.ApplicationSendPipeline
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.ihawu.core.masking.DefaultMaskingEngine
import org.ihawu.core.masking.MaskingFailureSink
import org.ihawu.core.masking.ResolverErrorMode
import org.ihawu.core.policy.IhawuPrincipal
import org.ihawu.core.policy.ResourcePolicy
import org.ihawu.core.policy.ResourcePolicyResolver
import org.ihawu.core.policy.RoleBasedResourcePolicyResolver
import org.ihawu.kotlinx.maskingContextElement
import org.ihawu.kotlinx.maskingRegistry

/**
 * Configuration for the [IhawuKtor] plugin — the analogue of the Spring Boot starter's beans.
 *
 * Supply, at minimum, the field-policy source ([policies] or [policyResolver]) and the
 * [resources] registry; wire [resolvePrincipal] to your `Authentication` to identify the caller.
 */
class IhawuKtorConfig {
    /**
     * Resolves the caller for each request. The default fails closed — a `null` principal masks the
     * whole resource (`{}`), matching the other adapters. In production map your `Authentication`
     * result here, e.g. `resolvePrincipal = { it.ihawuPrincipal() }`.
     */
    var resolvePrincipal: suspend (ApplicationCall) -> IhawuPrincipal? = { null }

    /** The JSON used to encode responses and decode request bodies. */
    var json: Json = Json

    /** Notified on each fail-closed drop; defaults to a no-op so the adapter needs no logger. */
    var onFailClosed: MaskingFailureSink = MaskingFailureSink { _, _, _, _ -> }

    /**
     * How a policy-resolver failure (a policy-store outage or misconfiguration) is handled. Defaults to
     * [ResolverErrorMode.MASK_ALL] — mask the whole resource fail-closed (`{}`, still `200`).
     * [ResolverErrorMode.FAIL_REQUEST] instead lets the error surface as a `500`, so an outage is not
     * silent (only the resolver-error path; a missing principal still masks). See ADR 0011.
     */
    var onPolicyFailure: ResolverErrorMode = ResolverErrorMode.MASK_ALL

    internal var policyResolver: ResourcePolicyResolver? = null
    internal val resourceEntries = mutableListOf<Pair<KSerializer<*>, String>>()

    /** Resolve field policies with a custom [ResourcePolicyResolver] (a DB, OPA, Casbin, …). */
    fun policyResolver(resolver: ResourcePolicyResolver) {
        policyResolver = resolver
    }

    /** Resolve field policies from static role-based [rules][ResourcePolicy]. */
    fun policies(vararg rules: ResourcePolicy) {
        policyResolver = RoleBasedResourcePolicyResolver(rules.toList())
    }

    /**
     * Register each `@IhawuResource` serializer under its resource name so it masks when serialized.
     * Register nested resource types and collection element types too, so they mask when reached.
     */
    fun resources(vararg entries: Pair<KSerializer<*>, String>) {
        resourceEntries += entries
    }
}

/**
 * The Ihawu masking plugin for Ktor. A single install gives `@IhawuResource` responses per-caller
 * masking with no per-route code:
 *
 * ```
 * install(IhawuKtor) {
 *     resolvePrincipal = { it.ihawuPrincipal() }
 *     policies(ResourcePolicy("employee", mapOf("MANAGER" to listOf(FieldPolicy("ssn", REDACT)))))
 *     resources(Employee.serializer() to "employee")
 * }
 * ```
 *
 * It folds in the `ContentNegotiation` registration (do not install it separately) and, per call,
 * wraps the pipeline in the caller's masking context via the kotlinx coroutine→thread-local bridge
 * (ADR 0009), so the synchronous encode sees the principal. No principal → `{}`.
 */
val IhawuKtor =
    createApplicationPlugin("IhawuKtor", ::IhawuKtorConfig) {
        val json = pluginConfig.json
        val resolvePrincipal = pluginConfig.resolvePrincipal
        val engine =
            DefaultMaskingEngine(
                pluginConfig.policyResolver ?: RoleBasedResourcePolicyResolver(emptyList()),
                pluginConfig.onFailClosed,
                pluginConfig.onPolicyFailure,
            )
        val registry = maskingRegistry(*pluginConfig.resourceEntries.toTypedArray())
        val converter = MaskingContentConverter(json, engine, registry)

        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, converter)
        }

        // Wrap the send pipeline — not the call pipeline — so the principal is resolved after
        // route-level Authentication has populated the call (a `Plugins`-phase wrap runs too early
        // and the auth bridge would see null). `call.respond(...)` starts this pipeline from inside
        // the authenticated handler, so `resolvePrincipal` sees the caller; the coroutine→thread-local
        // bridge carries the context onto the downstream ContentNegotiation encode (ADR 0009). The
        // masking engine is named `engine` locally, never exposed where it could shadow `Application.engine`.
        application.sendPipeline.intercept(ApplicationSendPipeline.Before) {
            withContext(maskingContextElement(resolvePrincipal(call))) {
                proceed()
            }
        }
    }

/**
 * The default identity bridge: reads an [IhawuPrincipal] straight from the call's `Authentication`
 * result, for apps whose auth provider yields an [IhawuPrincipal]. Returns `null` (fail-closed) when
 * the call is unauthenticated. Use as `resolvePrincipal = { it.ihawuPrincipal() }`.
 */
fun ApplicationCall.ihawuPrincipal(): IhawuPrincipal? = principal<IhawuPrincipal>()
