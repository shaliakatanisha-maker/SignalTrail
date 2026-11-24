// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail

import android.app.Application
import com.group5.signaltrail.data.CsvStore
import com.group5.signaltrail.data.SettingsStore
import com.group5.signaltrail.measure.ConnectionMonitor
import com.group5.signaltrail.measure.HttpProber
import com.group5.signaltrail.measure.SessionRunner
import com.group5.signaltrail.measure.SignalReader

/**
 * Holds the long-lived measurement objects. They live as long as the process (not the screen), so the
 * connection callback keeps logging while walking between locations and across screen rotations.
 */
class SignalTrailApp : Application() {
    lateinit var settings: SettingsStore; private set
    lateinit var csv: CsvStore; private set
    lateinit var monitor: ConnectionMonitor; private set
    lateinit var signal: SignalReader; private set
    lateinit var runner: SessionRunner; private set
    val prober = HttpProber()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        csv = CsvStore(this)
        monitor = ConnectionMonitor(this, csv).also { it.start() }
        signal = SignalReader(this).also { it.startCellular() }   // main thread: required by PhoneStateListener
        runner = SessionRunner(csv, monitor, signal, prober)
    }
}
