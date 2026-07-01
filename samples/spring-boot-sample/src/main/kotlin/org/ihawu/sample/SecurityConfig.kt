package org.ihawu.sample

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * HTTP Basic security with three in-memory demo users, one per role, so the masking difference can be
 * seen live with `curl -u <user>:password`. The role names (`HR_ADMIN`, `MANAGER`, `EMPLOYEE`) are the
 * keys the policy rules resolve against — Spring Security's `ROLE_` prefix is stripped by Ihawu's
 * principal bridge.
 */
@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .httpBasic(Customizer.withDefaults())
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .build()

    @Bean
    fun userDetailsService(): UserDetailsService {
        val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

        fun user(
            username: String,
            role: String,
        ) = User
            .withUsername(username)
            .password(encoder.encode("password"))
            .roles(role)
            .build()

        return InMemoryUserDetailsManager(
            user("hradmin", "HR_ADMIN"),
            user("manager", "MANAGER"),
            user("employee", "EMPLOYEE"),
        )
    }
}
