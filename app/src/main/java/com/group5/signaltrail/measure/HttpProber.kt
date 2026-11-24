// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.measure

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Result of one HTTP GET. outcome is "ok", "timeout" or "error". */
data class HttpResult(
    val outcome: String,
    val durationMs: Long,
    val bytes: Long,
    val httpCode: Int?,
    val detail: String
)

/**
 * One GET = one measurement. Timing follows the proposal:
 *  - clock: SystemClock.elapsedRealtime() (monotonic; not affected by wall-clock changes)
 *  - start: before the connection is opened, so DNS + TCP + TLS handshakes are INCLUDED. A brand-new
 *    connection is forced each time ("Connection: close"), so every probe pays the same setup cost.
 *    This is what a user feels on a fresh request, and it makes the 3 probes comparable.
 *  - end:   after the last body byte has been read.
 */
class HttpProber {
    // Single daemon thread that aborts requests running longer than the 15 s budget.
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "http-watchdog").apply { isDaemon = true }
    }

    suspend fun get(url: String): HttpResult = withContext(Dispatchers.IO) {
        val timedOut = AtomicBoolean(false)
        var conn: HttpURLConnection? = null
        var future: ScheduledFuture<*>? = null
        var bytes = 0L
        var code: Int? = null
        val start = SystemClock.elapsedRealtime()
        try {
            val c = URL(withNonce(url)).openConnection() as HttpURLConnection
            conn = c
            // connectTimeout/readTimeout apply per blocking call only, so a slow trickle could exceed 15 s.
            // The watchdog enforces a TOTAL 15 s budget by disconnecting, which makes the read throw.
            future = watchdog.schedule({ timedOut.set(true); c.disconnect() }, TIMEOUT_MS, TimeUnit.MILLISECONDS)
            c.connectTimeout = TIMEOUT_MS.toInt()
            c.readTimeout = TIMEOUT_MS.toInt()
            c.useCaches = false
            c.requestMethod = "GET"
            c.setRequestProperty("Cache-Control", "no-cache")
            c.setRequestProperty("Pragma", "no-cache")
            // Setting Accept-Encoding ourselves also stops HttpURLConnection from transparently gzipping.
            c.setRequestProperty("Accept-Encoding", "identity")
            c.setRequestProperty("Connection", "close")   // no keep-alive: new TCP+TLS setup every request
            c.setRequestProperty("User-Agent", "SignalTrail/1.0")

            code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            stream?.use { s ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    bytes += n
                }
            }
            val duration = SystemClock.elapsedRealtime() - start
            val encoding = c.getHeaderField("Content-Encoding")
            when {
                timedOut.get() || duration > TIMEOUT_MS -> HttpResult("timeout", duration, bytes, code, "exceeded 15 s")
                code !in 200..299 -> HttpResult("error", duration, bytes, code, "HTTP $code")
                encoding != null && encoding != "identity" ->
                    HttpResult("error", duration, bytes, code, "server used Content-Encoding $encoding")
                else -> HttpResult("ok", duration, bytes, code, "HTTP $code")
            }
        } catch (e: SocketTimeoutException) {
            HttpResult("timeout", SystemClock.elapsedRealtime() - start, bytes, code, "socket timeout")
        } catch (e: IOException) {
            val d = SystemClock.elapsedRealtime() - start
            if (timedOut.get()) HttpResult("timeout", d, bytes, code, "exceeded 15 s")
            else HttpResult("error", d, bytes, code, "${e.javaClass.simpleName}: ${e.message}")
        } catch (e: Exception) {
            // e.g. malformed URL, SecurityException on a locked-down ROM
            HttpResult("error", SystemClock.elapsedRealtime() - start, bytes, code,
                "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            future?.cancel(false)
            conn?.disconnect()
        }
    }

    /** Unique query string so no proxy or CDN can answer from a cache. */
    private fun withNonce(url: String): String =
        url + (if (url.contains('?')) "&" else "?") + "nonce=" + SystemClock.elapsedRealtimeNanos()

    companion object {
        const val TIMEOUT_MS = 15_000L
        const val EXPECTED_DOWNLOAD_BYTES = 1_048_576L   // 1 MiB
    }
}
