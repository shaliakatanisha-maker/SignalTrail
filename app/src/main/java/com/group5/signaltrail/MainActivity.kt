// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.group5.signaltrail.ui.DevicesScreen
import com.group5.signaltrail.ui.ExportScreen
import com.group5.signaltrail.ui.HistoryScreen
import com.group5.signaltrail.ui.MeasureScreen
import com.group5.signaltrail.ui.theme.SignalTrailTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    // READ_PHONE_STATE is a runtime permission on API 23+. Denial is fine: network type becomes NA.
    private val phonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onPhonePermissionHandled() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The proposal keeps the app open with the screen on during the walk (avoids Doze / background limits).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            SignalTrailTheme {
                AppRoot(vm, requestPhonePermission = { phonePermission.launch(Manifest.permission.READ_PHONE_STATE) })
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    MEASURE("Measure", Icons.Filled.PlayArrow),
    HISTORY("History", Icons.AutoMirrored.Filled.List),
    DEVICES("Devices", Icons.Filled.Phone),
    EXPORT("Export", Icons.Filled.Share)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: AppViewModel, requestPhonePermission: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.MEASURE) }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.bodySmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary,
                    titleContentColor = colors.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = colors.surface) {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; if (t == Tab.HISTORY) vm.reloadHistory() },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.primary,
                            selectedTextColor = colors.primary,
                            indicatorColor = colors.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.MEASURE -> MeasureScreen(vm)
                Tab.HISTORY -> HistoryScreen(vm)
                Tab.DEVICES -> DevicesScreen(vm)
                Tab.EXPORT -> ExportScreen(vm)
            }
        }
    }

    if (vm.showPhoneRationale) {
        AlertDialog(
            onDismissRequest = { vm.onPhonePermissionHandled() },
            title = { Text("Phone permission (optional)") },
            text = { Text(stringResource(R.string.phone_permission_rationale)) },
            confirmButton = { TextButton(onClick = requestPhonePermission) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { vm.onPhonePermissionHandled() }) { Text("Not now (record NA)") } }
        )
    } else if (vm.showNewDevicePrompt) {
        AlertDialog(
            onDismissRequest = { vm.dismissNewDevice() },
            title = { Text("New device detected. Add profile?") },
            text = {
                Column {
                    Text(vm.detected.summary)
                    Text(
                        "No profile has model \"${vm.detected.model}\". Add a new profile, or link it to an existing one:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    vm.profiles.forEach { p ->
                        TextButton(onClick = { vm.linkDetectedTo(p.id) }) { Text("Link to ${p.name}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.addDetectedProfile() }) { Text("Add profile") } },
            dismissButton = { TextButton(onClick = { vm.dismissNewDevice() }) { Text("Not now") } }
        )
    }
}
