package org.ihawu.kotlinx

import org.ihawu.core.masking.MaskingContext
import org.ihawu.core.policy.IhawuPrincipal

/** A per-call [MaskingContext]: carries the principal and a per-call memo map for policy caching. */
internal class SimpleMaskingContext(
    override val principal: IhawuPrincipal?,
) : MaskingContext {
    private val memo = HashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> memoize(
        key: String,
        compute: () -> T,
    ): T = memo.getOrPut(key, compute) as T
}
