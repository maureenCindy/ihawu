package org.ihawu.core.policy

/**
 * Represents an authenticated user requesting a resource.
 *
 * @property userId The user identifier
 * @property roles assigned to the [userId]
 * @property attributes The [userId] metadata that is relevant in policy evaluation
 */
data class IhawuPrincipal(
    val userId: String,
    val roles: Set<String>,
    val attributes: Map<String, String>,
)
