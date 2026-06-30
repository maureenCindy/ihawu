package com.ihawu.spring.boot.starter.configuration

import com.ihawu.spring.boot.starter.interceptor.IhawuJacksonHttpMessageConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Hooks Ihawu's masking into Spring MVC's response serialization.
 *
 * Gated to servlet web applications with Spring MVC present, so it never loads in non-web or reactive
 * apps. It replaces the autoconfigured Jackson converter with [IhawuJacksonHttpMessageConverter] —
 * the bridge that attaches the per-request principal at write time.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(WebMvcConfigurer::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class IhawuWebConfig {
    /**
     * Swaps the autoconfigured Jackson converter for [IhawuJacksonHttpMessageConverter], reusing its
     * `ObjectMapper`, so the per-request principal is attached at serialization time.
     *
     * Override by defining your own bean named `ihawuWebMvcConfigurer`. Note the conditional matches
     * on the bean *name*, not the [WebMvcConfigurer] type — apps (and Spring itself) routinely
     * contribute other `WebMvcConfigurer` beans, so a type match would suppress this one in most apps.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["ihawuWebMvcConfigurer"])
    fun ihawuWebMvcConfigurer(): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
                val idx = converters.indexOfFirst { it is MappingJackson2HttpMessageConverter }
                if (idx >= 0) {
                    val existing = converters[idx] as MappingJackson2HttpMessageConverter
                    converters[idx] = IhawuJacksonHttpMessageConverter(existing.objectMapper)
                }
            }
        }
}
