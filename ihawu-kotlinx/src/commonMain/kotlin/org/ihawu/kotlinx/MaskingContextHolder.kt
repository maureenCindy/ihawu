package org.ihawu.kotlinx

import org.ihawu.core.masking.MaskingContext

/**
 * The per-call [MaskingContext] for the current encode.
 *
 * kotlinx's encode path is synchronous and gives no per-call channel, so [MaskingJsonTransformer] can't
 * read a coroutine context from inside `transformSerialize`; the context is stashed here around the
 * encode instead. On the JVM this is a `ThreadLocal` (concurrent encodes on different threads don't
 * collide, and the Ktor coroutine bridge — see `maskingContextElement` — maps onto it). JS/native are
 * single-threaded, so a plain reference suffices.
 */
internal expect var maskingContext: MaskingContext?
