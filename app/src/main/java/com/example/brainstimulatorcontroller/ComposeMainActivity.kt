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
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.os.Looper


class ComposeMainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val bluetoothLeScanner: BluetoothLeScanner?
    get() = bluetoothAdapter?.bluetoothLeScanner
    // checks if bluetooth is enabled on device
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initializes the bluetooth adaptors
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permLauncher.launch(btPerms)
        } else {
            afterPermissionsGranted()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private val btPerms = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                afterPermissionsGranted()
            } else {
                Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Regardless of result, continue to UI
            setContent { SampleApp() }
        }

    /** Run AFTER runtime permissions are granted */
    private fun afterPermissionsGranted() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Safe to read isEnabled now
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || adapter.isEnabled) {
            setContent { SampleApp() }
        } else {
            // Show system dialog to enable Bluetooth
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        }
    }
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000
    private fun scanLeDevice() {
        if (!scanning) {
            // stop after SCAN_PERIOD
            handler.postDelayed({
                scanning = false
                stopScanSafe()
            }, SCAN_PERIOD)

            scanning = true
            startScanSafe()
        } else {
            scanning = false
            stopScanSafe()
        }
    }
    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasBtPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ).all { perm ->
                ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
            }
        } else true
    }

    @SuppressLint("MissingPermission")
    private fun startScanSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtPermissions()) return
        bluetoothLeScanner?.startScan(leScanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopScanSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtPermissions()) return
        bluetoothLeScanner?.stopScan(leScanCallback)
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
    private class LeDeviceListAdapter {
        private val devices = mutableListOf<BluetoothDevice>()
        fun addDevice(device: BluetoothDevice) {
            if (devices.none { it.address == device.address }) devices.add(device)
        }
        fun notifyDataSetChanged() { /* no-op for now */ }
        fun getDevices(): List<BluetoothDevice> = devices
    }

    // function to enable bluetooth
    private fun promptEnableBluetooth() {
        val adapter = bluetoothAdapter
        if (adapter != null && !adapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Bluetooth is already enabled", Toast.LENGTH_SHORT).show()
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
            // current slider
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
            Button(onClick = { promptEnableBluetooth() }) {
                Text("Enable Bluetooth")
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
}
