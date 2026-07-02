package com.example.smoke

import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.masking.MaskingStrategy
import org.ihawu.core.policy.FieldPolicy
import org.ihawu.core.policy.ResourcePolicy
import org.ihawu.spring.boot.starter.configuration.ResourcePolicyProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * A minimal Spring Boot consumer of the PUBLISHED `org.ihawu:ihawu-spring-boot-starter` artifact.
 *
 * It imports core types (`@IhawuResource`, `FieldPolicy`, …) that are only reachable if the packaged
 * starter exposes `ihawu-core` transitively via `api` — so the fact this compiles is half the proof.
 */
@SpringBootApplication
class SmokeApp {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .httpBasic(Customizer.withDefaults())
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .build()

    @Bean
    fun userDetailsService(): UserDetailsService {
        val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        return InMemoryUserDetailsManager(
            User.withUsername("manager").password(encoder.encode("password")).roles("MANAGER").build(),
        )
    }

    @Bean
    fun resourcePolicyProvider(): ResourcePolicyProvider =
        ResourcePolicyProvider {
            listOf(
                ResourcePolicy(
                    resourceName = "account",
                    roleFieldPolicies =
                        mapOf(
                            "MANAGER" to listOf(FieldPolicy("ssn", MaskingStrategy.REDACT, "***")),
                        ),
                ),
            )
        }
}

@IhawuResource("account")
data class AccountResponse(
    val id: String,
    val owner: String,
    val ssn: String,
)

@RestController
class AccountController {
    @GetMapping("/account")
    fun account(): AccountResponse = AccountResponse(id = "1", owner = "Jane Doe", ssn = "123-45-6789")
}

fun main(args: Array<String>) {
    runApplication<SmokeApp>(*args)
}
