// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.group5.signaltrail.ui.theme.statusColors

/** Rounded (12 dp) white card with a blue heading. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
            content()
        }
    }
}

/** Filled pill with a text label; text colour picked for contrast (dark text on amber). */
@Composable
fun StatusChip(text: String, color: Color) {
    val onColor = if (color.luminance() > 0.4f) Color(0xFF1F2933) else Color.White
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = onColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun OutcomeChip(outcome: String) {
    val c = statusColors()
    when (outcome) {
        "ok" -> StatusChip("OK", c.success)
        "timeout" -> StatusChip("Timeout", c.error)
        "mismatch" -> StatusChip("Connection mismatch", c.warning)
        "error" -> StatusChip("Error", c.error)
        else -> StatusChip(outcome, c.neutral)
    }
}

enum class Quality(val label: String) { STRONG("Strong"), FAIR("Fair"), WEAK("Weak"), NA("NA") }

/** Wi-Fi thresholds commonly used for RSSI: >= -60 dBm strong, -60..-70 fair, < -70 weak. */
fun wifiQuality(rssi: Int?): Quality = when {
    rssi == null -> Quality.NA
    rssi >= -60 -> Quality.STRONG
    rssi >= -70 -> Quality.FAIR
    else -> Quality.WEAK
}

fun cellQuality(level: Int?): Quality = when {
    level == null -> Quality.NA
    level >= 3 -> Quality.STRONG
    level == 2 -> Quality.FAIR
    else -> Quality.WEAK
}

@Composable
fun qualityColor(q: Quality): Color {
    val c = statusColors()
    return when (q) {
        Quality.STRONG -> c.success
        Quality.FAIR -> c.warning
        Quality.WEAK -> c.error
        Quality.NA -> c.neutral
    }
}

/** Horizontal bar gauge + numeric value + quality label. */
@Composable
fun SignalGauge(title: String, value: String, fraction: Float, quality: Quality, detail: String = "") {
    val color = qualityColor(quality)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            StatusChip(quality.label, color)
        }
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
        if (detail.isNotEmpty()) {
            Text(
                detail, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Progress bar "label  count / target". */
@Composable
fun ProgressRow(label: String, count: Int, target: Int) {
    val done = count >= target
    val color = if (done) statusColors().success else MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$count / $target", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            if (done) {
                Spacer(Modifier.width(8.dp))
                StatusChip("Done", color)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val f = if (target > 0) count.toFloat() / target else 0f
            Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
    }
}

/** "Key ........ value" row with monospace value. */
@Composable
fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.let(display) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}
