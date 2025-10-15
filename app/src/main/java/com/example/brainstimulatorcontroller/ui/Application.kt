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
    onSendSet: (channel: Int, currentMA: Float, freqHz: Int) -> Unit,
    onStart: (channel: Int) -> Unit,
    onStop: (channel: Int) -> Unit,
) {
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Brain Stimulator Sample Application") }) }
        ) { inner ->
            AppContent(
                devices = devices,
                onEnableBt = onEnableBt,
                onScanToggle = onScanToggle,
                onDeviceClick = onDeviceClick,
                onSendSet = onSendSet,
                onStart = onStart,
                onStop = onStop,
                modifier = Modifier.padding(inner).fillMaxSize()
            )
        }
    }
}

@Composable
private fun AppContent(
    devices: List<DeviceRow>,
    onEnableBt: () -> Unit,
    onScanToggle: () -> Unit,
    onDeviceClick: (DeviceRow) -> Unit,
    onSendSet: (channel: Int, currentMA: Float, freqHz: Int) -> Unit,
    onStart: (channel: Int) -> Unit,
    onStop: (channel: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var channelText by remember { mutableStateOf("1") }
    var currentMA by remember { mutableStateOf(2.5f) }
    var freqText by remember { mutableStateOf("1000") }
    var logs by remember { mutableStateOf(listOf<String>()) }

    fun log(line: String) { logs = listOf(line) + logs.take(100) }

    // Parse inputs
    val channel = channelText.toIntOrNull()
    val freqHz = freqText.toIntOrNull()

    val channelOk = channel != null && channel in 0..9
    val freqOk = freqHz != null && freqHz in 1..20000
    val inputsOk = channelOk && freqOk

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Controls", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = channelText,
                onValueChange = { channelText = it.filter { ch -> ch.isDigit() }.take(1) },
                label = { Text("Channel") },
                modifier = Modifier.width(120.dp),
                isError = !channelOk
            )
            OutlinedTextField(
                value = freqText,
                onValueChange = { freqText = it.filter { ch -> ch.isDigit() }.take(5) },
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
                    onSendSet(channel!!, currentMA, freqHz!!)
                    log("TX: SET ch=$channel I=${"%.1f".format(currentMA)}mA f=$freqHz Hz")
                }
            ) { Text("Send SET") }

            Button(
                enabled = channelOk,
                onClick = {
                    onStart(channel!!)
                    log("TX: START ch=$channel")
                }
            ) { Text("START") }

            Button(
                enabled = channelOk,
                onClick = {
                    onStop(channel!!)
                    log("TX: STOP ch=$channel")
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
        ) { items(logs) { Text(it) } }
    }
}
