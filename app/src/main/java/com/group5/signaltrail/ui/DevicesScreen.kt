// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.group5.signaltrail.AppViewModel
import com.group5.signaltrail.data.DeviceProfile
import com.group5.signaltrail.ui.theme.statusColors

@Composable
fun DevicesScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<DeviceProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DeviceProfile?>(null) }
    val status = statusColors()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("This device (auto-detected)") {
                KeyValue("Manufacturer", vm.detected.manufacturer)
                KeyValue("Model", vm.detected.model)
                KeyValue("Android", "${vm.detected.release} (API ${vm.detected.sdk})")
                KeyValue("Telephony hardware", if (vm.detected.hasTelephony) "yes" else "no")
                val matched = vm.profiles.firstOrNull { it.matches(vm.detected.model) }
                if (matched != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip("Matched", status.success)
                        Text("  profile \"${matched.name}\"")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip("No profile", status.warning)
                        Text("  add one or link an existing profile")
                    }
                    Button(onClick = { vm.addDetectedProfile() }, shape = RoundedCornerShape(12.dp)) {
                        Text("Add this device")
                    }
                }
            }
        }
        items(vm.profiles, key = { it.id }) { p ->
            SectionCard(p.name, action = {
                Row {
                    IconButton(onClick = { editing = p }) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${p.name}") }
                    IconButton(onClick = { deleting = p }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${p.name}", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }) {
                KeyValue("Model (Build.MODEL)", p.model.ifBlank { "-" })
                KeyValue("Android version", p.androidVersion.ifBlank { "-" })
                KeyValue("Has cellular", if (p.hasCellular) "yes" else "no")
                KeyValue("Wi-Fi standard", p.wifiStandard.ifBlank { "-" })
                if (p.id == vm.selectedProfileId) StatusChip("Selected on Measure", MaterialTheme.colorScheme.primary)
                else OutlinedButton(onClick = { vm.selectProfile(p.id) }) { Text("Use for measuring") }
            }
        }
        item {
            Button(
                onClick = { creating = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add profile")
            }
        }
    }

    if (creating || editing != null) {
        ProfileDialog(
            initial = editing,
            onSave = { vm.saveProfile(it); editing = null; creating = false },
            onDismiss = { editing = null; creating = false }
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("Only the profile is removed. Measurements already saved are kept.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteProfile(p.id); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileDialog(initial: DeviceProfile?, onSave: (DeviceProfile) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var android by remember { mutableStateOf(initial?.androidVersion ?: "") }
    var cellular by remember { mutableStateOf(initial?.hasCellular ?: false) }
    var wifi by remember { mutableStateOf(initial?.wifiStandard ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add device profile" else "Edit ${initial.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Friendly name") }, singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("Model (as Build.MODEL)") }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                OutlinedTextField(android, { android = it }, label = { Text("Android version") }, singleLine = true)
                OutlinedTextField(wifi, { wifi = it }, label = { Text("Wi-Fi standard (optional)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Has cellular (mobile data)", modifier = Modifier.weight(1f))
                    Switch(checked = cellular, onCheckedChange = { cellular = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = initial ?: DeviceProfile(name = "", model = "", androidVersion = "", hasCellular = false)
                    onSave(base.copy(name = name.trim(), model = model.trim(), androidVersion = android.trim(),
                        hasCellular = cellular, wifiStandard = wifi.trim()))
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
