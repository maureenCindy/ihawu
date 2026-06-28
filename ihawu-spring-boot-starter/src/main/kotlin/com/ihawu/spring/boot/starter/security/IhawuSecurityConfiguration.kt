package com.ihawu.spring.boot.starter.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.Authentication

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Authentication::class)
class IhawuSecurityConfiguration {
    @Bean
    @ConditionalOnMissingBean(PrincipalResolver::class)
    fun principalResolver(): PrincipalResolver = IhawuPrincipalResolver()

    @Bean
    @ConditionalOnMissingBean
    fun ihawuRequestFilter(resolver: PrincipalResolver): IhawuRequestFilter = IhawuRequestFilter(resolver)
}
