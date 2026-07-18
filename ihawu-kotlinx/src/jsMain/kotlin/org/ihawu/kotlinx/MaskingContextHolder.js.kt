package org.ihawu.kotlinx

import org.ihawu.core.masking.MaskingContext

// JS is single-threaded, so a plain reference is enough — no thread-local needed.
internal actual var maskingContext: MaskingContext? = null
