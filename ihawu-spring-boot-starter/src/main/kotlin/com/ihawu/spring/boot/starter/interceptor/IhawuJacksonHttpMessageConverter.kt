package com.ihawu.spring.boot.starter.interceptor

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.ihawu.core.serialization.IhawuSerialization
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

/**
 * The bridge between the per-request principal and the masking serializer.
 *
 * A drop-in replacement for the app's [MappingJackson2HttpMessageConverter] (reusing its
 * `ObjectMapper`). At write time it attaches the current request's principal to the [ObjectWriter] via
 * [ObjectWriter.withAttribute], which the core `MaskingPropertyWriter` reads back through
 * `SerializerProvider.getAttribute`. When there is no principal (unauthenticated, anonymous, or Spring
 * Security absent) no attribute is attached, so the serializer fails closed and emits `{}`.
 *
 * @param objectMapper the application's configured mapper (already carrying [com.ihawu.core.serialization.IhawuModule]).
 */
class IhawuJacksonHttpMessageConverter(
    objectMapper: ObjectMapper,
) : MappingJackson2HttpMessageConverter(objectMapper) {
    override fun customizeWriter(
        writer: ObjectWriter,
        type: JavaType?,
        contentType: MediaType?,
    ): ObjectWriter {
        val principal = IhawuRequestContext.ihawuPrincipal()
        return principal
            ?.let { writer.withAttribute(IhawuSerialization.PRINCIPAL, it) }
            ?: writer
    }
}
