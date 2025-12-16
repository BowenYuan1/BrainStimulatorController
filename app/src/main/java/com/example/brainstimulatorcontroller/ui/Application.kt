package com.example.brainstimulatorcontroller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.brainstimulatorcontroller.DeviceRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Application(
    devices: List<DeviceRow>,
    onEnableBt: () -> Unit,
    onScanToggle: () -> Unit,
    onDeviceClick: (DeviceRow) -> Unit,
    // CMD0
    onSetFrequency: (channelsMask: Int, freqHz: Int) -> Unit,

    // CMD1
    onSendSet: (
        channelsMask: Int,
        phase: Int,
        currentMA: Float,
        waveform: Int
    ) -> Unit,

    // CMD2
    onSetOnCounter: (channelsMask: Int, rampUp: Long) -> Unit,

    // CMD3
    onSetOffCounter: (channelsMask: Int, rampDown: Long) -> Unit,

    // CMD4
    onStart: (channelsMask: Int) -> Unit,

    // CMD5
    onStop: (channelsMask: Int) -> Unit,
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
                onSetFrequency = onSetFrequency,
                onSendSet = onSendSet,
                onSetOnCounter = onSetOnCounter,
                onSetOffCounter = onSetOffCounter,
                onStart = onStart,
                onStop = onStop,
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize(),
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
    onSetFrequency: (channelsMask: Int, freqHz: Int) -> Unit,
    onSendSet: (channelsMask: Int, phase: Int, currentMA: Float, waveform: Int) -> Unit,
    onSetOnCounter: (channelsMask: Int, rampUp: Long) -> Unit,
    onSetOffCounter: (channelsMask: Int, rampDown: Long) -> Unit,
    onStart: (channelsMask: Int) -> Unit,
    onStop: (channelsMask: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var phaseText by remember { mutableStateOf("0") }
    var currentMA by remember { mutableStateOf(2.5f) }
    var freqText by remember { mutableStateOf("1000") }

    var presetExpanded by remember { mutableStateOf(false) }
    var selectedPresetLabel by remember { mutableStateOf("Choose preset") }

    // channel selection state
    var ch1 by remember { mutableStateOf(false) }
    var ch2 by remember { mutableStateOf(false) }
    var ch3 by remember { mutableStateOf(false) }
    var ch4 by remember { mutableStateOf(false) }


    // Ramp up and down counters
    var onCountText by remember { mutableStateOf("0") }
    var offCountText by remember { mutableStateOf("0") }

    val rampUp  = onCountText.toLongOrNull()
    val rampDown = offCountText.toLongOrNull()
    val rampUpOk = rampUp != null
    val rampDownOk = rampDown != null

    // waveform dropdown state
    var waveformExpanded by remember { mutableStateOf(false) }
    var selectedWaveformLabel by remember { mutableStateOf("Sine wave") }
    val waveforms = listOf("Sine wave", "Triangle wave", "Sawtooth wave")

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

    val channelsMask =
        (if (ch4) 0b1000 else 0) or
                (if (ch3) 0b0100 else 0) or
                (if (ch2) 0b0010 else 0) or
                (if (ch1) 0b0001 else 0)
    val channelsOk = channelsMask != 0
    val phaseOk = phase != null && phase in 0..90
    val freqOk = freqHz != null && freqHz in 1..20000
    val inputsOk = phaseOk && freqOk && channelsOk

    // Map label → waveform bit: 0 = sine, 1 = triangle
    val waveformCode = when (selectedWaveformLabel) {
        "Triangle wave" -> 1
        "Sawtooth wave" -> 2
        else            -> 0
    }
    val scrollState = rememberScrollState()
    Column(modifier = modifier
        .verticalScroll(scrollState)
        .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Controls", style = MaterialTheme.typography.titleMedium)

        // Preset dropdown
        ExposedDropdownMenuBox(
            expanded = presetExpanded,
            onExpandedChange = { presetExpanded = !presetExpanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedPresetLabel,
                onValueChange = {},
                label = { Text("Presets") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = presetExpanded,
                onDismissRequest = { presetExpanded = false }
            ) {
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

        // Waveform dropdown
        ExposedDropdownMenuBox(
            expanded = waveformExpanded,
            onExpandedChange = { waveformExpanded = !waveformExpanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedWaveformLabel,
                onValueChange = {},
                label = { Text("Waveform") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = waveformExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = waveformExpanded,
                onDismissRequest = { waveformExpanded = false }
            ) {
                waveforms.forEach { label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            selectedWaveformLabel = label
                            waveformExpanded = false
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = phaseText,
                onValueChange = { phaseText = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Phase (degrees)") },
                modifier = Modifier.width(140.dp),
                isError = !phaseOk
            )
            OutlinedTextField(
                value = freqText,
                onValueChange = { freqText = it.filter { c -> c.isDigit() }.take(5) },
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
        Column {
            Text("Channels", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row {
                    Checkbox(checked = ch1, onCheckedChange = { ch1 = it })
                    Text("Ch 1")
                }
                Row {
                    Checkbox(checked = ch2, onCheckedChange = { ch2 = it })
                    Text("Ch 2")
                }
                Row {
                    Checkbox(checked = ch3, onCheckedChange = { ch3 = it })
                    Text("Ch 3")
                }
                Row {
                    Checkbox(checked = ch4, onCheckedChange = { ch4 = it })
                    Text("Ch 4")
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ramp Counters", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = onCountText,
                    onValueChange = { onCountText = it.filter { c -> c.isDigit() } },
                    label = { Text("On Count (CMD2)") },
                    modifier = Modifier.width(180.dp),
                    isError = !rampUpOk
                )
                OutlinedTextField(
                    value = offCountText,
                    onValueChange = { offCountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Off Count (CMD3)") },
                    modifier = Modifier.width(200.dp),
                    isError = !rampDownOk
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // CMD1 – Configure (phase, waveform, amplitude)
            Button(
                enabled = inputsOk,
                onClick = {
                    onSendSet(
                        channelsMask,
                        phase!!,
                        currentMA,
                        waveformCode
                    )
                }
            ) { Text("Configure (Set)") }

            // CMD4 – Enable
            Button(
                enabled = channelsOk,
                onClick = {
                    onStart(channelsMask)
                }
            ) { Text("Enable") }

            // CMD5 – Disable
            Button(
                enabled = channelsOk,
                onClick = {
                    onStop(channelsMask)
                }
            ) { Text("Disable") }

            // CMD0 – Set Frequency
            Button(
                enabled = channelsOk && freqOk,
                onClick = {
                    onSetFrequency(channelsMask, freqHz!!)
                }
            ) { Text("Set Frequency") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = channelsOk && rampUpOk,
                onClick = {
                    onSetOnCounter(channelsMask, rampUp!!)
                }
            ) { Text("Set ON Counter") }

            Button(
                enabled = channelsOk && rampDownOk,
                onClick = {
                    onSetOffCounter(channelsMask, rampDown!!)
                }
            ) { Text("Set OFF Counter") }
        }
        Divider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEnableBt) { Text("Enable Bluetooth") }
            Button(onClick = onScanToggle) { Text("Scan / Stop") }
        }

        Divider()

        Text("Devices", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
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
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
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
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs) { Text(it) }
        }
    }
}
