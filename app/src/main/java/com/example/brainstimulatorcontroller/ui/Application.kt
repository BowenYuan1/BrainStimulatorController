package com.example.brainstimulatorcontroller.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.brainstimulatorcontroller.DeviceRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Application(
    devices: List<DeviceRow>,
    onEnableBt: () -> Unit,
    onScanToggle: () -> Unit,
    onDeviceClick: (DeviceRow) -> Unit,
    onSendSet: (phase: Int, currentMA: Float, freqHz: Int) -> Unit,
    onStart: (phase: Int, currentMA: Float, freqHz: Int) -> Unit,
    onStop: (phase: Int) -> Unit,
    logs: List<String>,
) {

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Brain Stimulator Controller") }) }
        ) { inner ->
            AppContent(
                devices = devices,
                onEnableBt = onEnableBt,
                onScanToggle = onScanToggle,
                onDeviceClick = onDeviceClick,
                onSendSet = onSendSet,
                onStart = onStart,
                onStop = onStop,
                modifier = Modifier.padding(inner).fillMaxSize(),
                logs = logs
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(
    devices: List<DeviceRow>,
    logs: List<String>,
    onEnableBt: () -> Unit,
    onScanToggle: () -> Unit,
    onDeviceClick: (DeviceRow) -> Unit,
    onSendSet: (phase: Int, currentMA: Float, freqHz: Int) -> Unit,
    onStart: (phase: Int, currentMA: Float, freqHz: Int) -> Unit,
    onStop: (phase: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var phaseText by remember { mutableStateOf("0") }
    var currentMA by remember { mutableStateOf(2.5f) }
    var freqText by remember { mutableStateOf("1000") }

    var presetExpanded by remember { mutableStateOf(false) }
    var selectedPresetLabel by remember { mutableStateOf("Choose preset") }

    // Parse inputs
    val phase = phaseText.toIntOrNull()
    val freqHz = freqText.toIntOrNull()
    data class Preset(val label: String, val phase: Int, val currentMA: Float, val freqHz: Int)
    val presets = listOf(
        Preset("Tingle (low)", phase = 0, currentMA = 0.5f, freqHz = 10),
        Preset("Stim (moderate)", phase = 30, currentMA = 2.0f, freqHz = 100),
        Preset("Stim (high)", phase = 60, currentMA = 3.5f, freqHz = 1000),
        Preset("Custom baseline", phase = 0, currentMA = 1.0f, freqHz = 500)
    )
    val phaseOk = phase != null && phase in 0..90
    val freqOk = freqHz != null && freqHz in 1..20000
    val inputsOk = phaseOk && freqOk

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Controls", style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(expanded = presetExpanded, onExpandedChange = { presetExpanded = !presetExpanded }) {
            OutlinedTextField(
                readOnly = true,
                value = selectedPresetLabel,
                onValueChange = {},
                label = { Text("Presets") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
                presets.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.label) },
                        onClick = {
                            phaseText = p.phase.toString()
                            currentMA = p.currentMA
                            freqText = p.freqHz.toString()
                            selectedPresetLabel = p.label
                            presetExpanded = false
                        }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = phaseText,
                onValueChange = { phaseText = it.filter { phase -> phase.isDigit() }.take(1) },
                label = { Text("Phase Control (degrees)") },
                modifier = Modifier.width(120.dp),
                isError = !phaseOk
            )
            OutlinedTextField(
                value = freqText,
                onValueChange = { freqText = it.filter { phase -> phase.isDigit() }.take(5) },
                label = { Text("Frequency (Hz)") },
                modifier = Modifier.width(180.dp),
                isError = !freqOk
            )
        }

        Column {
            Text("Current (mA): ${"%.1f".format(currentMA)}")
            Slider(
                value = currentMA,
                onValueChange = { currentMA = it },
                valueRange = 0f..5f,
                steps = 49
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = inputsOk,
                onClick = {
                    onSendSet(phase!!, currentMA, freqHz!!) // When pressed set data
                }
            ) { Text("Send SET (Modify Instruction)") }

            Button(
                enabled = phaseOk,
                onClick = {
                    onStart(phase!!, currentMA, freqHz!!)
                }
            ) { Text("START") }

            Button(
                enabled = phaseOk,
                onClick = {
                    onStop(phase!!)
                }
            ) { Text("STOP") }
        }

        Divider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEnableBt) { Text("Enable Bluetooth") }
            Button(onClick = onScanToggle) { Text("Scan / Stop") }
        }

        Divider()

        Text("Devices", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = devices,
                key = { it.address.ifBlank { it.name } }
            ) { row ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDeviceClick(row) }
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge)
                        if (row.address.isNotBlank()) {
                            Text(row.address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Text("Log", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs) { Text(it) }
        }
    }
}
