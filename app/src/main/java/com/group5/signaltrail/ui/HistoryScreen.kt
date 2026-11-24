// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.group5.signaltrail.AppViewModel
import com.group5.signaltrail.data.SessionSummary
import com.group5.signaltrail.data.conditionName
import com.group5.signaltrail.measure.TimeWindow
import com.group5.signaltrail.ui.theme.statusColors
import java.util.Locale

private const val ALL = "All"

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val sessions = vm.sessions
    var fDevice by rememberSaveable { mutableStateOf(ALL) }
    var fLocation by rememberSaveable { mutableStateOf(ALL) }
    var fWindow by rememberSaveable { mutableStateOf(ALL) }
    var fCondition by rememberSaveable { mutableStateOf(ALL) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf(false) }

    val filtered = sessions.filter {
        (fDevice == ALL || it.device == fDevice) &&
            (fLocation == ALL || it.location == fLocation) &&
            (fWindow == ALL || it.timeWindow == fWindow) &&
            (fCondition == ALL || it.condition == fCondition)
    }.asReversed()   // newest first

    // Progress: every condition of every profile, plus any device/condition found only in the data.
    val progress = (vm.profiles.flatMap { p ->
        listOfNotNull(p.name to "wifi", if (p.hasCellular) p.name to "cellular" else null)
    } + sessions.map { it.device to it.intended }).distinct()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("Progress", action = { TextButton(onClick = { editTarget = true }) { Text("Target: ${vm.target}") } }) {
                if (progress.isEmpty()) Text("No device profiles yet.")
                progress.forEach { (device, intended) ->
                    ProgressRow(conditionName(device, intended), vm.sessionCount(device, intended), vm.target)
                }
                Text("${sessions.size} sessions saved in total.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("Filters") {
                DropdownField("Device", listOf(ALL) + sessions.map { it.device }.distinct(), fDevice, { it }, { fDevice = it })
                DropdownField("Location", listOf(ALL) + vm.locations.union(sessions.map { it.location }), fLocation, { it }, { fLocation = it })
                DropdownField("Time window", listOf(ALL) + TimeWindow.values().map { it.csv }, fWindow,
                    { if (it == ALL) ALL else TimeWindow.labelOf(it) }, { fWindow = it })
                DropdownField("Condition", listOf(ALL) + sessions.map { it.condition }.distinct(), fCondition, { it }, { fCondition = it })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} sessions shown", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = sessions.isNotEmpty() && !vm.running,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete last session") }
            }
        }
        items(filtered, key = { it.id }) { SessionCard(it) }
    }

    if (confirmDelete) {
        val last = sessions.lastOrNull()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete last session?") },
            text = {
                Text("This removes ${last?.id} (${last?.location}, ${last?.condition}) from measurements.csv and " +
                    "signal_samples.csv. Connection events are kept. Use this only for a session recorded by mistake.")
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteLastSession(); confirmDelete = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    if (editTarget) {
        var text by remember { mutableStateOf(vm.target.toString()) }
        AlertDialog(
            onDismissRequest = { editTarget = false },
            title = { Text("Sessions per device-condition") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Target (default 30 = 5 × 2 × 3)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { text.toIntOrNull()?.let { vm.updateTarget(it) }; editTarget = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editTarget = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(s: SessionSummary) {
    val status = statusColors()
    SectionCard(s.location) {
        Text("${s.condition} · ${TimeWindow.labelOf(s.timeWindow)} · Day ${s.day} · Run ${s.run}",
            style = MaterialTheme.typography.bodyMedium)
        Text("${s.date} ${s.time} · ${s.id}", style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val latency = if (s.okLatencies.isEmpty()) "NA"
        else String.format(Locale.US, "%.0f ms", s.okLatencies.average())
        val speed = s.speedMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: "NA"
        Text("Latency avg $latency (${s.okLatencies.size}/${s.probeCount} OK) · Download $speed",
            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.allOk) StatusChip("OK", status.success)
            s.outcomeCounts.filterKeys { it != "ok" }.forEach { (outcome, n) ->
                val (label, color) = when (outcome) {
                    "timeout" -> "Timeout" to status.error
                    "mismatch" -> "Connection mismatch" to status.warning
                    "error" -> "Error" to status.error
                    else -> outcome to status.neutral
                }
                StatusChip("$label ×$n", color)
            }
            if (s.connectionChanged) StatusChip("Connection changed", status.warning)
        }
    }
}
