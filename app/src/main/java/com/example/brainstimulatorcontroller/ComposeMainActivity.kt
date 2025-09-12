package com.example.brainstimulatorcontroller  // <-- keep this matching your package

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.core.content.ContextCompat


class ComposeMainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private val btPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    // checks if bluetooth is enabled on device
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔹 1. Get the Bluetooth manager and adapter

        // Check if the device supports Bluetooth
        if (bluetoothAdapter == null) {
            // Device doesn’t support Bluetooth
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish() // close app
            return
        }

        // Ensure Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled) {
            // Prompt user to enable it
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        var scanning = false
        setContent {
            SampleApp()
        }

    }

    private val bluetoothManager: BluetoothManager =
        getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
            }
        }
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    private var scanning = false
    private val handler = Handler()
    private val SCAN_PERIOD: Long = 10000
    private fun scanLeDevice() {
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                scanning = false
                bluetoothLeScanner.stopScan(leScanCallback)
            }, SCAN_PERIOD)
            scanning = true
            bluetoothLeScanner.startScan(leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner.stopScan(leScanCallback)
        }
    }
    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasBtPermissions(): Boolean =
        btPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun startScanSafe() {
        if (!hasBtPermissions()) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        scanner.startScan(leScanCallback)
    }
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun stopScanSafe() {
        if (!hasBtPermissions()) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        scanner.stopScan(leScanCallback)
    }
    private val leDeviceListAdapter = LeDeviceListAdapter()

    // Device scan callback
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            leDeviceListAdapter.addDevice(result.device)
            leDeviceListAdapter.notifyDataSetChanged()
        }


    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable // UI building block to show the title of the application
    fun SampleApp() {
        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Brain Stimulator Sample Application") }
                    )
                }

            ) { innerPadding ->
                AppContent(Modifier.padding(innerPadding).fillMaxSize())
            }
        }
    }

    @Composable // UI code for the interactables within the applicaiton.
    fun AppContent(modifier: Modifier = Modifier) {
        // sets default values.
        var channel by remember { mutableStateOf("1") }
        var currentMA by remember { mutableStateOf(2.5f) }
        var freqHz by remember { mutableStateOf("1000") }
        var logs by remember { mutableStateOf(listOf<String>()) }

        fun log(line: String) {
            logs = listOf(line) + logs.take(100)
        }

        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Controls", style = MaterialTheme.typography.titleMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // channels textbox
                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it.filter { ch -> ch.isDigit() }.take(1) },
                    label = { Text("Channel") },
                    modifier = Modifier.width(120.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                // frequency textbox
                OutlinedTextField(
                    value = freqHz,
                    onValueChange = { freqHz = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Frequency (Hz)") },
                    modifier = Modifier.width(180.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Column {
                Text("Current (mA): ${"%.1f".format(currentMA)}")
                Slider(
                    value = currentMA,
                    onValueChange = { currentMA = it },
                    valueRange = 0f..5f,
                    steps = 49 // 0.1 mA steps
                )
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

            Text("Log", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { Text(it) }
            }
        }
    }
}
