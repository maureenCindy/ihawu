package org.ihawu.kotlinx

import kotlinx.coroutines.asContextElement
import org.ihawu.core.masking.MaskingContext
import org.ihawu.core.policy.IhawuPrincipal
import kotlin.coroutines.CoroutineContext

private val holder = ThreadLocal<MaskingContext?>()

internal actual var maskingContext: MaskingContext?
    get() = holder.get()
    set(value) = holder.set(value)

/**
 * A coroutine context element that installs [principal]'s [MaskingContext] on whichever thread runs the
 * (synchronous) encode — the bridge for coroutine callers such as Ktor (#82):
 *
 * ```
 * withContext(maskingContextElement(principal)) { json.encodeToString(serializer, value) }
 * ```
 */
public fun maskingContextElement(principal: IhawuPrincipal?): CoroutineContext = holder.asContextElement(SimpleMaskingContext(principal))
