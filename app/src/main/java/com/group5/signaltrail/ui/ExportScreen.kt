// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.group5.signaltrail.AppViewModel
import java.io.File

@Composable
fun ExportScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    // Storage Access Framework "Save as…" - no storage permission needed (Android 4.4+, so fine on 7 and 14).
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val name = pendingName
        pendingName = null
        message = if (uri != null && name != null) vm.copyToUri(name, uri) else "Save cancelled"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val files = remember(refresh) { vm.exportFiles.map { it to vm.rowCount(it) } }
        files.forEach { (file, rows) ->
            SectionCard(file.name) {
                KeyValue("Rows", rows.toString())
                KeyValue("Size", if (file.exists()) "${file.length()} bytes" else "not created yet")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { message = shareFiles(context, listOf(file)) },
                        enabled = file.exists(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Share") }
                    OutlinedButton(
                        onClick = { pendingName = file.name; saveLauncher.launch(file.name) },
                        enabled = file.exists()
                    ) { Text("Save as…") }
                }
            }
        }
        Button(
            onClick = { message = shareFiles(context, vm.exportFiles) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Share all CSV files") }
        TextButton(onClick = { refresh++ }) { Text("Refresh counts") }
        if (message.isNotEmpty()) Text(message, fontFamily = FontFamily.Monospace)
        SectionCard("Where the files are") {
            Text(vm.dataDir.absolutePath, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text(
                "Rows are flushed to disk one by one, so files survive app restarts. " +
                    "Android deletes this folder when the app is UNINSTALLED - export after every measurement day.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Share through the system share sheet using FileProvider content:// URIs (no storage permission). */
private fun shareFiles(context: Context, files: List<File>): String {
    val existing = files.filter { it.exists() && it.length() > 0 }
    if (existing.isEmpty()) return "Nothing to share yet"
    val authority = "${context.packageName}.fileprovider"
    val uris = ArrayList(existing.map { FileProvider.getUriForFile(context, authority, it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.type = "text/csv"
    intent.putExtra(Intent.EXTRA_SUBJECT, "SignalTrail data")
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // ClipData makes the read grant reach the receiving app on all Android versions.
    val clip = ClipData.newUri(context.contentResolver, "csv", uris[0])
    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    intent.clipData = clip
    return try {
        context.startActivity(Intent.createChooser(intent, "Share CSV files"))
        "Opened share sheet (${existing.size} file(s))"
    } catch (e: Exception) {
        "Share failed: ${e.message}"
    }
}
