package com.ihawu.spring.boot.starter.interceptor

import com.ihawu.core.policy.IhawuPrincipal
import com.ihawu.core.serialization.IhawuSerialization
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Reads the current request's [IhawuPrincipal], bridging the servlet request attribute set by
 * [com.ihawu.spring.boot.starter.security.IhawuPrincipalFilter] to the serialization layer.
 *
 * The principal is fetched from the active request via `RequestContextHolder`, so it works from
 * `customizeWriter` (which has no request parameter). Returns `null` off a request thread, or when no
 * principal was attached — both of which make masking fail closed.
 */
object IhawuRequestContext {
    /** The [IhawuPrincipal] attached to the current request, or `null` if none / not on a request thread. */
    fun ihawuPrincipal(): IhawuPrincipal? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)
            ?.request
            ?.getAttribute(IhawuSerialization.PRINCIPAL) as? IhawuPrincipal
}
