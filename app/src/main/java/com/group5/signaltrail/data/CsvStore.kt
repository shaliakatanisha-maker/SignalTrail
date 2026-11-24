// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only CSV files. Every row is written, flushed and fsync'ed immediately, so data survives an
 * app crash, a restart or a flat battery. Missing values are written as "NA".
 *
 * Location: app-specific external storage (Android/data/com.group5.signaltrail/files/data/) so that
 * `adb pull` works; falls back to internal storage. No storage permission is needed for either.
 * NOTE: Android deletes both folders on uninstall - export after every measurement day.
 */
class CsvStore(context: Context) {
    val dir: File = (context.getExternalFilesDir(null) ?: context.filesDir).let { File(it, "data") }
        .apply { mkdirs() }

    val measurements = File(dir, "measurements.csv")
    val signalSamples = File(dir, "signal_samples.csv")
    val connectionEvents = File(dir, "connection_events.csv")
    val allFiles: List<File> get() = listOf(measurements, signalSamples, connectionEvents)

    fun appendMeasurement(values: List<Any?>) = append(measurements, MEASUREMENT_HEADER, values)
    fun appendSample(values: List<Any?>) = append(signalSamples, SAMPLE_HEADER, values)
    fun appendEvent(values: List<Any?>) = append(connectionEvents, EVENT_HEADER, values)

    @Synchronized
    private fun append(file: File, header: List<String>, values: List<Any?>) {
        require(values.size == header.size) { "${file.name}: ${values.size} values for ${header.size} columns" }
        val writeHeader = !file.exists() || file.length() == 0L
        val sb = StringBuilder()
        if (writeHeader) sb.append(toLine(header))
        sb.append(toLine(values))
        FileOutputStream(file, true).use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
    }

    @Synchronized
    fun readRows(file: File): List<Map<String, String>> {
        if (!file.exists()) return emptyList()
        val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = parseLine(lines[0])
        return lines.drop(1).map { line ->
            val f = parseLine(line)
            header.indices.associate { header[it] to f.getOrElse(it) { "" } }
        }
    }

    @Synchronized
    fun rowCount(file: File): Int =
        if (!file.exists()) 0 else (file.readLines().count { it.isNotBlank() } - 1).coerceAtLeast(0)

    /** Removes one session from measurements.csv and signal_samples.csv (connection events are kept). */
    @Synchronized
    fun deleteSession(sessionId: String) {
        for (file in listOf(measurements, signalSamples)) {
            if (!file.exists()) continue
            val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val idx = parseLine(lines[0]).indexOf("session_id")
            if (idx < 0) continue
            val kept = listOf(lines[0]) + lines.drop(1).filter { parseLine(it).getOrNull(idx) != sessionId }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(kept.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    companion object {
        val MEASUREMENT_HEADER = listOf(
            "date", "session_id", "day", "time_window", "run", "location", "device_name",
            "device_manufacturer", "device_model", "android_version", "api_level", "has_cellular",
            "intended_connection", "actual_connection", "wifi_frequency_mhz", "rssi_dbm",
            "cell_level_0_4", "cell_network_type", "request_type", "latency_ms", "bytes_received",
            "duration_ms", "speed_mbps", "outcome", "connection_changed",
            // extra columns (after the required ones): wall-clock time, monotonic clock, error text
            "time_local", "elapsed_realtime_ms", "detail"
        )
        val SAMPLE_HEADER = listOf(
            "date", "time_local", "elapsed_realtime_ms", "session_id", "location", "device_name",
            "intended_connection", "actual_connection", "rssi_dbm", "wifi_frequency_mhz",
            "wifi_link_speed_mbps", "cell_level_0_4", "cell_dbm", "cell_network_type"
        )
        val EVENT_HEADER = listOf(
            "date", "time_local", "elapsed_realtime_ms", "event", "network_id", "transport",
            "validated", "downstream_kbps", "last_location", "session_id"
        )

        /** Date and time of the same instant: ("2026-11-24", "09:15:02.123"). */
        fun stamp(): Pair<String, String> {
            val now = Date()
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now) to
                SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(now)
        }

        private fun format(v: Any?): String = when (v) {
            null -> "NA"
            is Double -> if (v.isNaN() || v.isInfinite()) "NA" else String.format(Locale.US, "%.3f", v)
            is Float -> if (v.isNaN() || v.isInfinite()) "NA" else String.format(Locale.US, "%.3f", v)
            else -> v.toString()
        }

        private fun escape(raw: String): String {
            val s = raw.replace('\n', ' ').replace('\r', ' ')
            return if (s.contains(',') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s
        }

        private fun toLine(values: List<Any?>): String =
            values.joinToString(",") { escape(format(it)) } + "\n"

        /** Minimal RFC-4180 parser for one line (we never write newlines inside fields). */
        fun parseLine(line: String): List<String> {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                    c == '"' -> inQuotes = !inQuotes
                    c == ',' && !inQuotes -> { out.add(cur.toString()); cur.setLength(0) }
                    else -> cur.append(c)
                }
                i++
            }
            out.add(cur.toString())
            return out
        }
    }
}

/** One measurement session rebuilt from its rows in measurements.csv (used by the History screen). */
class SessionSummary(val id: String, val rows: List<Map<String, String>>) {
    private val first = rows.first()
    val date = first["date"].orEmpty()
    val time = first["time_local"].orEmpty().take(8)
    val device = first["device_name"].orEmpty()
    val intended = first["intended_connection"].orEmpty()
    val location = first["location"].orEmpty()
    val timeWindow = first["time_window"].orEmpty()
    val day = first["day"].orEmpty()
    val run = first["run"].orEmpty()
    val condition = conditionName(device, intended)

    val okLatencies: List<Double> = rows
        .filter { it["request_type"] == "probe" && it["outcome"] == "ok" }
        .mapNotNull { it["latency_ms"]?.toDoubleOrNull() }
    val probeCount = rows.count { it["request_type"] == "probe" }
    val speedMbps: Double? = rows
        .firstOrNull { it["request_type"] == "download" && it["outcome"] == "ok" }
        ?.get("speed_mbps")?.toDoubleOrNull()
    val outcomeCounts: Map<String, Int> = rows.groupingBy { it["outcome"].orEmpty() }.eachCount()
    val connectionChanged = rows.any { it["connection_changed"] == "true" }
    val allOk = rows.all { it["outcome"] == "ok" }

    companion object {
        fun fromRows(rows: List<Map<String, String>>): List<SessionSummary> =
            rows.groupBy { it["session_id"].orEmpty() }
                .filterKeys { it.isNotBlank() }
                .map { (id, r) -> SessionSummary(id, r) }
    }
}
