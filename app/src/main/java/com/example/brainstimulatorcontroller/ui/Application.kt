package com.example.brainstimulatorcontroller.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Application(
    devices: List<BluetoothDevice>,
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
    devices: List<BluetoothDevice>,
    onEnableBt: () -> Unit,
    onScanToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Default settings
    var channel by remember { mutableStateOf("1") }
    var currentMA by remember { mutableStateOf(2.5f) }
    var freqHz by remember { mutableStateOf("1000") }
    var logs by remember { mutableStateOf(listOf<String>()) }

    fun log(line: String) { logs = listOf(line) + logs.take(100) }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // inputs for the bluetooth packet
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
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        )
        // displays device lists
        // TODO: need to make the list clickable to pair
        {
            items(devices) { dev ->
                ElevatedCard {
                    val name = if (ActivityCompat.checkSelfPermission(
                            this as Context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return@ElevatedCard
                    } else {

                    }
                    dev.name ?: "(Unnamed)"
                    Text("$name\n${dev.address}")
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(dev.address, style = MaterialTheme.typography.bodySmall)
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
