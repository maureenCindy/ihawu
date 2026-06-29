package com.ihawu.spring.boot.starter.security

import com.ihawu.core.policy.IhawuPrincipal
import org.springframework.security.core.Authentication

/**
 * Maps a Spring Security [Authentication] to a framework-neutral [IhawuPrincipal].
 *
 * The starter's seam for identity: provide your own bean to override the default
 * ([IhawuPrincipalResolver]) - e.g. to read OIDC/JWT claims.
 */
interface PrincipalResolver {
    /**
     * @param authentication the Spring Security [Authentication]
     * @return the resolved principal, or `null` when there is no usable identity
     *   (unauthenticated or anonymous). A `null` principal makes Ihawu fail closed —
     *   the response is masked rather than exposed.
     */
    fun resolve(authentication: Authentication): IhawuPrincipal?
}
