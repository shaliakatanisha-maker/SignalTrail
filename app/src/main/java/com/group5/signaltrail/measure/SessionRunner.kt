// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.measure

import android.os.Build
import android.os.SystemClock
import com.group5.signaltrail.data.CsvStore
import com.group5.signaltrail.data.DeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale

enum class TimeWindow(val csv: String, val label: String) {
    MORNING("morning", "Morning 09:00–10:00"),
    LUNCH("lunch", "Lunch 12:15–13:15");

    companion object {
        fun forNow(): TimeWindow =
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 11) MORNING else LUNCH

        fun labelOf(csv: String): String = values().firstOrNull { it.csv == csv }?.label ?: csv
    }
}

data class SessionParams(
    val profile: DeviceProfile,
    val intended: ConnType,
    val location: String,
    val day: Int,
    val timeWindow: TimeWindow,
    val run: Int,
    val serverUrl: String
)

data class RequestRow(
    val type: String,          // "probe" or "download"
    val index: Int,
    val actual: ConnType,
    val outcome: String,       // ok | timeout | error | mismatch
    val latencyMs: Long?,
    val durationMs: Long?,
    val bytes: Long,
    val speedMbps: Double?,
    val connectionChanged: Boolean,
    val detail: String,
    val signal: SignalSnapshot
)

data class SessionResult(
    val sessionId: String,
    val params: SessionParams,
    val samples: List<SignalSnapshot>,
    val rows: List<RequestRow>
)

/**
 * Runs one session exactly as in proposal Section 6:
 *  1. wait for the connection to stabilise (signal sampled every 5 s during the whole session)
 *  2. 3 x GET /probe, then 1 x GET /download (1 MiB), each with a 15 s budget
 *  3. connection type checked before and after every request; every outcome is saved as a row.
 */
class SessionRunner(
    private val csv: CsvStore,
    private val monitor: ConnectionMonitor,
    private val signal: SignalReader,
    private val prober: HttpProber
) {
    suspend fun run(
        p: SessionParams,
        onStatus: (String) -> Unit,
        onSample: (SignalSnapshot) -> Unit
    ): SessionResult = withContext(Dispatchers.IO) {   // never on the main thread
        val sessionId = newSessionId(p.profile.name)
        monitor.sessionId = sessionId
        monitor.lastLocation = p.location
        val samples = Collections.synchronizedList(mutableListOf<SignalSnapshot>())

        fun takeSample() {
            val s = signal.snapshot()
            samples.add(s)
            writeSample(sessionId, p, s)
            onSample(s)
        }

        val sampler = launch {
            while (isActive) {
                takeSample()
                delay(SAMPLE_INTERVAL_MS)
            }
        }
        try {
            for (sec in STABILISE_SECONDS downTo 1) {
                onStatus("Waiting for the connection to stabilise… $sec s")
                delay(1_000)
            }
            val base = p.serverUrl.trim().trimEnd('/')
            val rows = mutableListOf<RequestRow>()
            for (i in 1..PROBE_COUNT) {
                onStatus("Latency probe $i of $PROBE_COUNT…")
                rows += measure(sessionId, p, "probe", i, "$base/probe")
            }
            onStatus("Downloading 1 MiB…")
            rows += measure(sessionId, p, "download", 1, "$base/download")
            sampler.cancelAndJoin()
            takeSample()   // a final reading right after the download
            SessionResult(sessionId, p, samples.toList(), rows)
        } finally {
            sampler.cancel()
            monitor.sessionId = ""
        }
    }

    private suspend fun measure(sessionId: String, p: SessionParams, type: String, index: Int, url: String): RequestRow {
        val sig = signal.snapshot()
        val before = monitor.activeType()
        val row = if (before != p.intended) {
            // Wrong network (e.g. Wi-Fi came back during a mobile-data test): do NOT send the request over
            // the wrong interface, but keep a row so the failure is visible in the data.
            RequestRow(type, index, before, "mismatch", null, null, 0, null, false,
                "active=${before.csv}, intended=${p.intended.csv}; request not sent", sig)
        } else {
            val switchesBefore = monitor.switchCount
            val r = prober.get(url)
            val after = monitor.activeType()
            val changed = after != before || monitor.switchCount != switchesBefore
            var outcome = r.outcome
            var detail = r.detail
            if (type == "download" && outcome == "ok" && r.bytes != HttpProber.EXPECTED_DOWNLOAD_BYTES) {
                outcome = "error"
                detail = "expected ${HttpProber.EXPECTED_DOWNLOAD_BYTES} bytes, got ${r.bytes}"
            }
            // Mbps = bytes * 8 / (seconds * 1,000,000)  (proposal Section 6)
            val speed = if (type == "download" && outcome == "ok" && r.durationMs > 0)
                r.bytes * 8.0 / (r.durationMs / 1000.0 * 1_000_000.0) else null
            if (changed) detail += "; connection changed (now ${after.csv})"
            RequestRow(type, index, before, outcome,
                latencyMs = if (type == "probe" && outcome == "ok") r.durationMs else null,
                durationMs = r.durationMs, bytes = r.bytes, speedMbps = speed,
                connectionChanged = changed, detail = detail, signal = sig)
        }
        writeMeasurement(sessionId, p, row)
        return row
    }

    private fun writeMeasurement(sessionId: String, p: SessionParams, r: RequestRow) {
        val (date, time) = CsvStore.stamp()
        csv.appendMeasurement(listOf(
            date, sessionId, p.day, p.timeWindow.csv, p.run, p.location, p.profile.name,
            Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, p.profile.hasCellular,
            p.intended.csv, r.actual.csv, r.signal.wifiFreqMhz, r.signal.rssiDbm,
            r.signal.cellLevel, r.signal.cellNetworkType, r.type, r.latencyMs, r.bytes,
            r.durationMs, r.speedMbps, r.outcome, r.connectionChanged,
            time, SystemClock.elapsedRealtime(), r.detail
        ))
    }

    private fun writeSample(sessionId: String, p: SessionParams, s: SignalSnapshot) {
        val (date, time) = CsvStore.stamp()
        csv.appendSample(listOf(
            date, time, s.elapsedMs, sessionId, p.location, p.profile.name, p.intended.csv,
            monitor.activeType().csv, s.rssiDbm, s.wifiFreqMhz, s.wifiLinkMbps,
            s.cellLevel, s.cellDbm, s.cellNetworkType
        ))
    }

    private fun newSessionId(deviceName: String): String {
        val tag = deviceName.filter { it.isLetterOrDigit() }.uppercase(Locale.US).take(8).ifEmpty { "DEV" }
        return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + "-" + tag
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 5_000L
        const val STABILISE_SECONDS = 10L
        const val PROBE_COUNT = 3
    }
}
