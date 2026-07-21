package org.ihawu.ktor

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.ihawu.core.masking.MaskingEngine
import org.ihawu.kotlinx.maskingSerializer

/**
 * A Ktor [ContentConverter] that masks responses through the kotlinx backend.
 *
 * Ktor's `json()` converter resolves each type's default serializer, so a `SerializersModule`
 * `contextual` registration would not intercept `@Serializable` classes (ADR 0009). Instead this
 * converter resolves the response type's serializer and wraps it with
 * [maskingSerializer][org.ihawu.kotlinx.maskingSerializer], which masks only the `@IhawuResource`
 * types present in [registry] and passes everything else through untouched — so it is safe to wrap
 * **every** response.
 *
 * The caller reaches the (synchronous) encode via a thread-local installed per call by [IhawuKtor];
 * with no principal in scope the resource fails closed to `{}`.
 *
 * Request bodies are not masked: [deserialize] delegates to the stock kotlinx converter.
 *
 * @property json The JSON used to encode responses and decode request bodies.
 * @property engine The masking engine that decides each field.
 * @property registry The `serialName -> resourceName` map that drives masking recursion.
 */
public class MaskingContentConverter(
    private val json: Json,
    private val engine: MaskingEngine,
    private val registry: Map<String, String>,
) : ContentConverter {
    private val reader = KotlinxSerializationConverter(json)

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?,
    ): OutgoingContent {
        val kotlinType = requireNotNull(typeInfo.kotlinType) { "Cannot mask a response without a Kotlin type: $typeInfo" }
        val base = json.serializersModule.serializer(kotlinType)
        // Thread this Json's discriminator key so sealed @IhawuResource subtypes mask (ADR 0010); relying
        // on the "type" default would silently fail open if the app configured a custom classDiscriminator.
        // Thread the module too so OPEN (non-sealed) subtypes resolve via getPolymorphic (ADR 0010).
        val masking = maskingSerializer(base, engine, registry, json.configuration.classDiscriminator, json.serializersModule)
        return TextContent(json.encodeToString(masking, value), contentType.withCharset(charset))
    }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel,
    ): Any? = reader.deserialize(charset, typeInfo, content)
}
