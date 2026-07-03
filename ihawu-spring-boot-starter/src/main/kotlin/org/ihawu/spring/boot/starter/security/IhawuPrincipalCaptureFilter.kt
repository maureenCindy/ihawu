package org.ihawu.spring.boot.starter.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.ihawu.core.serialization.IhawuSerialization
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Captures the authenticated identity once per request and exposes it for the serialization bridge.
 *
 * Resolves the current Spring Security `Authentication` to an [org.ihawu.core.policy.IhawuPrincipal]
 * via [principalResolver] and stores it as a request attribute under [IhawuSerialization.PRINCIPAL],
 * where [org.ihawu.spring.boot.starter.interceptor.IhawuRequestContext] reads it at write time.
 * Unauthenticated requests carry a `null` principal, so masking fails closed.
 *
 * @property principalResolver maps the `Authentication` to an [org.ihawu.core.policy.IhawuPrincipal].
 */
internal class IhawuPrincipalCaptureFilter(
    private val principalResolver: PrincipalResolver,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        val ihawuPrincipal = authentication?.let(principalResolver::resolve)
        request.setAttribute(IhawuSerialization.PRINCIPAL, ihawuPrincipal)
        filterChain.doFilter(request, response)
    }
}
