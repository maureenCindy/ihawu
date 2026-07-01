package org.ihawu.spring.boot.starter.security

import org.ihawu.core.policy.IhawuPrincipal
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails

/**
 * Default [PrincipalResolver]: maps the current [Authentication] to an [IhawuPrincipal]:
 * - `userId` ← the principal username (or [Authentication.getName]);
 * - `roles` ← authorities prefixed `ROLE_`, with the prefix stripped;
 * - `attributes` ← the username plus any map-valued `details`.
 *
 * Unauthenticated or [AnonymousAuthenticationToken] requests resolve to `null` (fail closed).
 */
class IhawuPrincipalResolver : PrincipalResolver {
    override fun resolve(authentication: Authentication): IhawuPrincipal? =
        if (!authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            null
        } else {
            IhawuPrincipal(
                userId = extractUserId(authentication),
                roles = extractRoles(authentication),
                attributes = extractAttributes(authentication),
            )
        }

    private fun extractUserId(authentication: Authentication): String =
        when (val principal = authentication.principal) {
            is String -> principal
            is UserDetails -> principal.username
            else -> authentication.name
        }

    private fun extractRoles(authentication: Authentication): Set<String> =
        authentication.authorities
            .map { it.authority }
            .filter { it.startsWith("ROLE_") }
            .map { it.removePrefix("ROLE_") }
            .toSet()

    private fun extractAttributes(authentication: Authentication): Map<String, String> =
        buildMap {
            put("username", authentication.name)
            (authentication.details as? Map<*, *>)?.forEach { (key, value) ->
                put("detail_$key", value.toString())
            }
        }
}
