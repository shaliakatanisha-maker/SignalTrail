// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.group5.signaltrail.data.DetectedDevice
import com.group5.signaltrail.data.DeviceProfile
import com.group5.signaltrail.data.SessionSummary
import com.group5.signaltrail.measure.ConnType
import com.group5.signaltrail.measure.SessionParams
import com.group5.signaltrail.measure.SessionResult
import com.group5.signaltrail.measure.SignalSnapshot
import com.group5.signaltrail.measure.TimeWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** All UI state. Compose reads the mutableStateOf fields directly. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SignalTrailApp
    private val settings = app.settings

    val activeConnection: StateFlow<ConnType> = app.monitor.current
    val detected: DetectedDevice = DetectedDevice.read(application)

    // ---- device profiles ----
    var profiles by mutableStateOf(settings.loadProfiles()); private set
    var selectedProfileId by mutableStateOf<String?>(null); private set
    val selectedProfile: DeviceProfile? get() = profiles.firstOrNull { it.id == selectedProfileId }
    var showNewDevicePrompt by mutableStateOf(false); private set
    var showPhoneRationale by mutableStateOf(false); private set

    // ---- session setup ----
    var locations by mutableStateOf(settings.locations); private set
    var location by mutableStateOf(settings.locations.first()); private set
    var condition by mutableStateOf(ConnType.WIFI); private set
    var day by mutableIntStateOf(1); private set
    var timeWindow by mutableStateOf(TimeWindow.forNow()); private set
    var runText by mutableStateOf("1")
    var serverUrl by mutableStateOf(settings.serverUrl); private set
    var target by mutableIntStateOf(settings.sessionTarget); private set

    // ---- live / results ----
    var live by mutableStateOf<SignalSnapshot?>(null); private set
    var running by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var lastResult by mutableStateOf<SessionResult?>(null); private set
    var sessions by mutableStateOf<List<SessionSummary>>(emptyList()); private set
    var serverTest by mutableStateOf(""); private set

    init {
        autoMatchProfile()
        if (app.signal.hasTelephonyFeature && !app.signal.hasPhoneStatePermission() && !settings.phonePermissionAsked) {
            showPhoneRationale = true
        }
        reloadHistory()
    }

    // ---------------- profiles ----------------

    private fun autoMatchProfile() {
        val match = profiles.firstOrNull { it.matches(detected.model) }
        if (match != null) {
            selectedProfileId = match.id
        } else {
            selectedProfileId = profiles.firstOrNull { it.id == settings.lastProfileId }?.id ?: profiles.firstOrNull()?.id
            showNewDevicePrompt = settings.dismissedModel != detected.model
        }
    }

    fun selectProfile(id: String) {
        selectedProfileId = id
        settings.lastProfileId = id
        suggestRun()
    }

    fun saveProfile(p: DeviceProfile) {
        profiles = if (profiles.any { it.id == p.id }) profiles.map { if (it.id == p.id) p else it } else profiles + p
        settings.saveProfiles(profiles)
        if (selectedProfileId == null) selectedProfileId = p.id
    }

    fun deleteProfile(id: String) {
        profiles = profiles.filterNot { it.id == id }
        settings.saveProfiles(profiles)
        if (selectedProfileId == id) selectedProfileId = profiles.firstOrNull()?.id
    }

    fun addDetectedProfile() {
        val p = DeviceProfile(
            name = detected.friendlyName, model = detected.model, androidVersion = detected.release,
            hasCellular = detected.hasTelephony
        )
        saveProfile(p)
        selectProfile(p.id)
        showNewDevicePrompt = false
    }

    /** The pre-loaded profile had a different Build.MODEL: copy the real model into it. */
    fun linkDetectedTo(id: String) {
        profiles.firstOrNull { it.id == id }?.let {
            saveProfile(it.copy(model = detected.model, androidVersion = detected.release))
            selectProfile(id)
        }
        showNewDevicePrompt = false
    }

    fun dismissNewDevice() {
        settings.dismissedModel = detected.model
        showNewDevicePrompt = false
    }

    fun onPhonePermissionHandled() {
        settings.phonePermissionAsked = true
        showPhoneRationale = false
    }

    // ---------------- setup ----------------

    /**
     * Wi-Fi is always offered. Mobile data is offered only when the profile has cellular AND this device
     * can actually use mobile data (a cellular network exists, or the data SIM is ready - when the user has
     * switched mobile data off for a Wi-Fi test the network disappears, but the SIM stays ready).
     */
    fun availableConditions(profile: DeviceProfile?, active: ConnType): List<ConnType> {
        if (profile == null || !profile.hasCellular) return listOf(ConnType.WIFI)
        val dataDetected = active == ConnType.CELLULAR || app.monitor.hasCellularNetwork() || app.signal.simReady()
        return if (dataDetected) listOf(ConnType.WIFI, ConnType.CELLULAR) else listOf(ConnType.WIFI)
    }

    fun selectCondition(c: ConnType) { condition = c; suggestRun() }
    fun selectLocation(l: String) { location = l; suggestRun() }
    fun selectDay(d: Int) { day = d; suggestRun() }
    fun selectTimeWindow(t: TimeWindow) { timeWindow = t; suggestRun() }

    fun updateServerUrl(url: String) {
        serverUrl = url
        settings.serverUrl = url.trim()
    }

    fun saveLocations(list: List<String>) {
        val clean = list.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { return }
        locations = clean
        settings.locations = clean
        if (location !in clean) location = clean.first()
        suggestRun()
    }

    fun updateTarget(n: Int) {
        target = n.coerceAtLeast(1)
        settings.sessionTarget = target
    }

    /** Next run number = sessions already saved for this exact device/condition/location/day/time + 1. */
    private fun suggestRun() {
        val name = selectedProfile?.name ?: return
        val n = sessions.count {
            it.device == name && it.intended == condition.csv && it.location == location &&
                it.day == day.toString() && it.timeWindow == timeWindow.csv
        }
        runText = (n + 1).toString()
    }

    fun sessionCount(deviceName: String, intendedCsv: String): Int =
        sessions.count { it.device == deviceName && it.intended == intendedCsv }

    /** Returns why Start is not allowed, or null if a session can start. */
    fun startBlocker(active: ConnType): String? {
        val profile = selectedProfile
        return when {
            running -> "A measurement is running"
            profile == null -> "Add or select a device profile (Devices tab)"
            location.isBlank() -> "Select a location"
            (runText.toIntOrNull() ?: 0) < 1 -> "Run number must be 1 or more"
            !serverUrl.trim().let { it.startsWith("https://") || it.startsWith("http://") } || serverUrl.trim().length < 12 ->
                "Enter the server base URL (https://…)"
            active != condition -> when (condition) {
                ConnType.WIFI -> "Connection mismatch: active is ${active.label}. Connect to campus Wi-Fi and turn mobile data off."
                else -> "Connection mismatch: active is ${active.label}. Turn Wi-Fi off and mobile data on."
            }
            else -> null
        }
    }

    // ---------------- measuring ----------------

    fun refreshLive() {
        if (running) return
        viewModelScope.launch {
            app.monitor.refresh()
            live = withContext(Dispatchers.IO) { app.signal.snapshot() }
        }
    }

    fun startSession() {
        val profile = selectedProfile ?: return
        if (startBlocker(activeConnection.value) != null) return
        val params = SessionParams(
            profile = profile, intended = condition, location = location, day = day,
            timeWindow = timeWindow, run = runText.toIntOrNull() ?: 1, serverUrl = serverUrl.trim()
        )
        running = true
        status = "Starting…"
        lastResult = null
        viewModelScope.launch {
            try {
                val result = app.runner.run(
                    params,
                    // callbacks arrive on IO threads; hop to the main thread before touching UI state
                    onStatus = { s -> viewModelScope.launch { status = s } },
                    onSample = { s -> viewModelScope.launch { live = s } }
                )
                lastResult = result
                val ok = result.rows.count { it.outcome == "ok" }
                status = "Saved ${result.sessionId} ($ok/${result.rows.size} requests OK)"
                loadSessions()
                suggestRun()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Session failed: ${e.javaClass.simpleName}: ${e.message}"
            } finally {
                running = false
            }
        }
    }

    /** Pilot helper: one probe to check the server is reachable. Nothing is saved. */
    fun testServer() {
        val base = serverUrl.trim().trimEnd('/')
        serverTest = "Testing…"
        viewModelScope.launch {
            val r = app.prober.get("$base/probe")
            serverTest = if (r.outcome == "ok") "Server OK · ${r.durationMs} ms (not saved)"
            else "${r.outcome.uppercase()}: ${r.detail}"
        }
    }

    // ---------------- history / export ----------------

    private suspend fun loadSessions() {
        sessions = withContext(Dispatchers.IO) {
            SessionSummary.fromRows(app.csv.readRows(app.csv.measurements))
        }
    }

    fun reloadHistory() {
        viewModelScope.launch { loadSessions(); suggestRun() }
    }

    fun deleteLastSession() {
        val last = sessions.lastOrNull() ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.csv.deleteSession(last.id) }
            if (lastResult?.sessionId == last.id) lastResult = null
            loadSessions()
            suggestRun()
        }
    }

    val exportFiles: List<File> get() = app.csv.allFiles
    val dataDir: File get() = app.csv.dir
    fun rowCount(f: File): Int = app.csv.rowCount(f)

    /** Storage Access Framework copy (works on Android 7 and 14 without storage permissions). */
    fun copyToUri(fileName: String, uri: Uri): String {
        val file = exportFiles.firstOrNull { it.name == fileName } ?: return "File not found"
        if (!file.exists()) return "$fileName has no data yet"
        return try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return "Could not open destination"
            "Saved $fileName (${file.length()} bytes)"
        } catch (e: Exception) {
            "Save failed: ${e.message}"
        }
    }
}
