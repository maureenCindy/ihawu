package com.ihawu.spring.boot.starter.security

import com.ihawu.core.serialization.IhawuSerialization
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class IhawuRequestFilter(
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
