package com.ihawu.spring.boot.starter

import com.ihawu.spring.boot.starter.security.IhawuPrincipalResolver
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IhawuPrincipalResolverTest {
    private val resolver = IhawuPrincipalResolver()

    @Test
    fun `maps an authenticated user to userId, ROLE-stripped roles, and attributes`() {
        val authentication =
            UsernamePasswordAuthenticationToken(
                "alice",
                null,
                listOf(SimpleGrantedAuthority("ROLE_ADMIN"), SimpleGrantedAuthority("SCOPE_read")),
            )

        val principal = resolver.resolve(authentication)

        assertEquals("alice", principal?.userId)
        assertEquals(setOf("ADMIN"), principal?.roles) // ROLE_ stripped; non-ROLE_ authority ignored
        assertEquals("alice", principal?.attributes?.get("username"))
    }

    @Test
    fun `derives userId from a UserDetails principal`() {
        val user =
            User
                .withUsername("bob")
                .password("x")
                .roles("USER")
                .build()
        val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)

        val principal = resolver.resolve(authentication)

        assertEquals("bob", principal?.userId)
        assertEquals(setOf("USER"), principal?.roles)
    }

    @Test
    fun `resolves an anonymous token to null`() {
        val authentication =
            AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")),
            )

        assertNull(resolver.resolve(authentication))
    }

    @Test
    fun `resolves an unauthenticated token to null`() {
        val authentication = UsernamePasswordAuthenticationToken.unauthenticated("alice", null)

        assertNull(resolver.resolve(authentication))
    }
}
