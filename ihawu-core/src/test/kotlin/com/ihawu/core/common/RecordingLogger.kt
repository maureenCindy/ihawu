package com.ihawu.core.common

import org.slf4j.ILoggerFactory
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.AbstractLogger
import org.slf4j.helpers.BasicMDCAdapter
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.MessageFormatter
import org.slf4j.spi.SLF4JServiceProvider
import java.util.concurrent.CopyOnWriteArrayList

/** A single recorded log event: its [level] and the fully formatted [message]. */
data class LogLine(
    val level: Level,
    val message: String,
)

/**
 * In-memory sink for log events captured during tests.
 *
 * Backs a test-only SLF4J binding ([RecordingServiceProvider]) so tests can assert on what was
 * logged without pulling a real logging framework (and its CVE surface) onto the classpath. Clear
 * it before each test with [clear].
 */
object LogRecorder {
    val lines = CopyOnWriteArrayList<LogLine>()

    fun clear() = lines.clear()
}

private class RecordingLogger(
    private val loggerName: String,
) : AbstractLogger() {
    override fun getName() = loggerName

    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        pattern: String?,
        arguments: Array<out Any>?,
        throwable: Throwable?,
    ) {
        LogRecorder.lines.add(LogLine(level, MessageFormatter.basicArrayFormat(pattern, arguments)))
    }

    override fun isTraceEnabled() = true

    override fun isTraceEnabled(marker: Marker?) = true

    override fun isDebugEnabled() = true

    override fun isDebugEnabled(marker: Marker?) = true

    override fun isInfoEnabled() = true

    override fun isInfoEnabled(marker: Marker?) = true

    override fun isWarnEnabled() = true

    override fun isWarnEnabled(marker: Marker?) = true

    override fun isErrorEnabled() = true

    override fun isErrorEnabled(marker: Marker?) = true
}

/**
 * Test-only SLF4J 2.x binding that routes every log call into [LogRecorder].
 *
 * Registered via `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`. Because it is the only
 * binding on the test classpath, SLF4J selects it automatically — no real logging framework needed.
 */
class RecordingServiceProvider : SLF4JServiceProvider {
    private val loggerFactory = ILoggerFactory { name -> RecordingLogger(name) }

    override fun getLoggerFactory() = loggerFactory

    override fun getMarkerFactory() = BasicMarkerFactory()

    override fun getMDCAdapter() = BasicMDCAdapter()

    override fun getRequestedApiVersion() = "2.0.99"

    override fun initialize() = Unit
}
