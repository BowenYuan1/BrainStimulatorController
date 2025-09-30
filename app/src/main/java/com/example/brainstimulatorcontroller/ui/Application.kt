package com.example.brainstimulatorcontroller.ui

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
    devices: List<DeviceRow>,       // now rows with name + address
    onEnableBt: () -> Unit,
    onScanToggle: () -> Unit
) {
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Brain Stimulator Sample Application") }) }
        ) { inner ->
            AppContent(
                devices = devices,
                onEnableBt = onEnableBt,
                onScanToggle = onScanToggle,
                modifier = Modifier.padding(inner).fillMaxSize()
            )
        }
    }
}

@Composable
fun AppContent(
    devices: List<DeviceRow>,
    onEnableBt: () -> Unit,
    onScanToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var channel by remember { mutableStateOf("1") }
    var currentMA by remember { mutableStateOf(2.5f) }
    var freqHz by remember { mutableStateOf("1000") }
    var logs by remember { mutableStateOf(listOf<String>()) }

    fun log(line: String) { logs = listOf(line) + logs.take(100) }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Controls", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = channel,
                onValueChange = { channel = it.filter { ch -> ch.isDigit() }.take(1) },
                label = { Text("Channel") },
                modifier = Modifier.width(120.dp)
            )
            OutlinedTextField(
                value = freqHz,
                onValueChange = { freqHz = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("Frequency (Hz)") },
                modifier = Modifier.width(180.dp)
            )
        }

        Column {
            Text("Current (mA): ${"%.1f".format(currentMA)}")
            Slider(value = currentMA, onValueChange = { currentMA = it }, valueRange = 0f..5f, steps = 49)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val payload = mapOf(
                    "cmd" to "SET",
                    "ch" to channel.toIntOrNull(),
                    "amp_mA" to "%.1f".format(currentMA),
                    "freq_Hz" to freqHz.toIntOrNull(),
                    "wave" to "sine",
                    "ramp_s" to 5.0
                )
                log("TX: $payload")
            }) { Text("Send SET") }

            Button(onClick = { log("TX: START ch=$channel") }) { Text("START") }
            Button(onClick = { log("TX: STOP ch=$channel") }) { Text("STOP") }
        }

        Divider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEnableBt) { Text("Enable Bluetooth") }
            Button(onClick = onScanToggle) { Text("Scan / Stop") }
        }

        Divider()

        Text("Devices", style = MaterialTheme.typography.titleMedium)

        // Show device NAME (primary) and ADDRESS (secondary)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = devices,
                key = { it.address.ifBlank { it.name } }  // stable enough for list rendering
            ) { row ->
                ElevatedCard {
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
        ) { items(logs) { Text(it) } }
    }
}
