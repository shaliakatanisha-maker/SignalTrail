// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.group5.signaltrail.AppViewModel
import com.group5.signaltrail.data.conditionName
import com.group5.signaltrail.measure.ConnType
import com.group5.signaltrail.measure.SessionResult
import com.group5.signaltrail.measure.SignalSnapshot
import com.group5.signaltrail.measure.TimeWindow
import com.group5.signaltrail.ui.theme.statusColors
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasureScreen(vm: AppViewModel) {
    val active by vm.activeConnection.collectAsState()
    val profile = vm.selectedProfile
    val conditions = vm.availableConditions(profile, active)
    val status = statusColors()

    // Keep the selected condition valid when the profile or available networks change.
    LaunchedEffect(conditions) {
        if (vm.condition !in conditions) vm.selectCondition(conditions.first())
    }
    // Live signal while idle (cheap: reads cached values; no Wi-Fi scan is triggered).
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshLive()
            delay(2_000)
        }
    }

    var editLocations by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- device & condition ----------
        SectionCard("Device & condition") {
            Text("This device: ${vm.detected.summary}", style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownField(
                label = "Device profile",
                options = vm.profiles,
                selected = profile,
                display = { "${it.name} (${if (it.hasCellular) "Wi-Fi + mobile" else "Wi-Fi only"})" },
                onSelect = { vm.selectProfile(it.id) },
                enabled = !vm.running
            )
            if (profile != null && !profile.matches(vm.detected.model)) {
                Text("⚠ Profile model \"${profile.model}\" differs from this device (${vm.detected.model}).",
                    color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
            }
            Text("Condition", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.forEach { c ->
                    FilterChip(
                        selected = vm.condition == c,
                        onClick = { vm.selectCondition(c) },
                        enabled = !vm.running,
                        label = { Text(conditionName(profile?.name ?: "Device", c.csv)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                        )
                    )
                }
            }
            if (profile != null) {
                ProgressRow(
                    "Sessions: ${conditionName(profile.name, vm.condition.csv)}",
                    vm.sessionCount(profile.name, vm.condition.csv), vm.target
                )
            }
        }

        // ---------- session setup ----------
        SectionCard("Session", action = {
            TextButton(onClick = { editLocations = true }, enabled = !vm.running) { Text("Edit locations") }
        }) {
            DropdownField(
                label = "Location", options = vm.locations, selected = vm.location,
                display = { it }, onSelect = { vm.selectLocation(it) }, enabled = !vm.running
            )
            Text("Day", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { d ->
                    FilterChip(selected = vm.day == d, onClick = { vm.selectDay(d) }, enabled = !vm.running,
                        label = { Text("Day $d") })
                }
            }
            Text("Time window", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeWindow.values().forEach { t ->
                    FilterChip(selected = vm.timeWindow == t, onClick = { vm.selectTimeWindow(t) },
                        enabled = !vm.running, label = { Text(t.label) })
                }
            }
            OutlinedTextField(
                value = vm.runText,
                onValueChange = { v -> vm.runText = v.filter { it.isDigit() }.take(3) },
                label = { Text("Run number") },
                singleLine = true,
                enabled = !vm.running,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.serverUrl,
                onValueChange = { vm.updateServerUrl(it) },
                label = { Text("Server base URL (saved)") },
                placeholder = { Text("https://example.trycloudflare.com") },
                singleLine = true,
                enabled = !vm.running,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            if (vm.serverUrl.trim().startsWith("http://")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip("Plain HTTP", status.warning)
                    Spacer(Modifier.width(8.dp))
                    Text("Pilot only - the proposal requires HTTPS.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.testServer() }, enabled = !vm.running) { Text("Test server") }
                Spacer(Modifier.width(12.dp))
                Text(vm.serverTest, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }

        // ---------- live status ----------
        SectionCard("Live status") {
            val mismatch = active != vm.condition
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active connection: ", style = MaterialTheme.typography.bodyMedium)
                Text(active.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (mismatch) StatusChip("Connection mismatch", status.warning) else StatusChip("OK", status.success)
            }
            if (mismatch) {
                Text(
                    "Selected: ${vm.condition.label}. " + if (vm.condition == ConnType.WIFI)
                        "Turn mobile data OFF and connect to campus Wi-Fi." else "Turn Wi-Fi OFF and mobile data ON.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            LiveGauges(vm.live)
        }

        // ---------- start ----------
        val blocker = vm.startBlocker(active)
        Button(
            onClick = { vm.startSession() },
            enabled = blocker == null,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (vm.running) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Measuring…")
            } else {
                Text("Start measurement", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (blocker != null && !vm.running) {
            Text(blocker, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (vm.status.isNotEmpty()) {
            Text(vm.status, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }

        vm.lastResult?.let { ResultCard(it) }
        Spacer(Modifier.height(8.dp))
    }

    if (editLocations) {
        LocationsDialog(
            initial = vm.locations,
            onSave = { vm.saveLocations(it); editLocations = false },
            onDismiss = { editLocations = false }
        )
    }
}

@Composable
private fun LiveGauges(s: SignalSnapshot?) {
    val rssi = s?.rssiDbm
    SignalGauge(
        title = "Wi-Fi RSSI",
        value = rssi?.let { "$it dBm" } ?: "NA",
        fraction = rssi?.let { (it + 100) / 60f } ?: 0f,     // -100 dBm -> 0 %, -40 dBm -> 100 %
        quality = wifiQuality(rssi),
        detail = listOfNotNull(
            s?.wifiFreqMhz?.let { "$it MHz" + if (it > 4900) " (5 GHz)" else " (2.4 GHz)" },
            s?.wifiLinkMbps?.let { "link $it Mbps" }
        ).joinToString(" · ")
    )
    val level = s?.cellLevel
    SignalGauge(
        title = "Cellular level",
        value = level?.let { "$it / 4" } ?: "NA",
        fraction = level?.let { it / 4f } ?: 0f,
        quality = cellQuality(level),
        detail = listOfNotNull(s?.cellDbm?.let { "$it dBm" }, s?.cellNetworkType).joinToString(" · ")
    )
}

@Composable
private fun ResultCard(r: SessionResult) {
    val status = statusColors()
    SectionCard("Result") {
        Text(r.sessionId, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Text("${r.params.location} · ${conditionName(r.params.profile.name, r.params.intended.csv)} · " +
            "${r.params.timeWindow.label} · Day ${r.params.day} · Run ${r.params.run}",
            style = MaterialTheme.typography.bodySmall)
        if (r.rows.any { it.connectionChanged }) StatusChip("Connection changed - excluded from main comparison", status.warning)
        HorizontalDivider()
        Text("Signal samples (every 5 s)", style = MaterialTheme.typography.labelLarge)
        val t0 = r.samples.firstOrNull()?.elapsedMs ?: 0L
        r.samples.forEach { s ->
            Text(
                String.format(Locale.US, "t+%4.1fs  RSSI %s  cell %s",
                    (s.elapsedMs - t0) / 1000.0,
                    s.rssiDbm?.let { "$it dBm" } ?: "NA",
                    s.cellLevel?.let { "L$it" } ?: "NA"),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall
            )
        }
        HorizontalDivider()
        r.rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val label = if (row.type == "probe") "Probe ${row.index}" else "Download"
                val value = if (row.type == "probe") row.latencyMs?.let { "$it ms" } ?: "${row.durationMs ?: "NA"} ms"
                else row.speedMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: "NA"
                Text(label, modifier = Modifier.width(90.dp))
                Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutcomeChip(row.outcome)
            }
        }
        r.rows.firstOrNull { it.type == "download" }?.let { d ->
            KeyValue("Bytes received", "${d.bytes} / 1048576")
            KeyValue("Download duration", d.durationMs?.let { "$it ms" } ?: "NA")
            if (d.outcome != "ok") Text(d.detail, style = MaterialTheme.typography.bodySmall)
        }
        r.rows.filter { it.type == "probe" && it.outcome != "ok" }.forEach {
            Text("Probe ${it.index}: ${it.detail}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LocationsDialog(initial: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit locations") },
        text = {
            Column {
                Text("One location per line (e.g. \"P1 Central Court\").", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 5,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.lines()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
