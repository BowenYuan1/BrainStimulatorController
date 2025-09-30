package com.example.brainstimulatorcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import com.example.brainstimulatorcontroller.ui.Application

// ---- New: lightweight row model for the UI ----
data class DeviceRow(
    val name: String,
    val address: String
)

class ComposeMainActivity : ComponentActivity() {

    // Bluetooth core variables
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private companion object { const val TAG = "BT_SCAN" }

    private fun addLog(msg: String) = android.util.Log.d(TAG, msg)
    private fun hasPerm(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private val btPerms = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private fun hasBtPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btPerms.all { p ->
                ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
            }
        } else true
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) afterPermissionsGranted()
            else {
                Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            setContent {
                Application(
                    devices = deviceRows,               // <-- pass rows with names
                    onEnableBt = { promptEnableBluetooth() },
                    onScanToggle = { scanLeDevice() }
                )
            }
        }

    // Scan state / timing
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD_MS = 10_000L

    // expose DeviceRow to UI instead of raw BluetoothDevice
    private val deviceRows = mutableStateListOf<DeviceRow>()

    private var bluetoothGatt: BluetoothGatt? = null
    //private val gattCallback = object : BluetoothGattCallback() {  }

    private val leScanCallback = object : ScanCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return

            // Prefer advertised name then device.name else "(Unnamed)"
            val advName = result.scanRecord?.deviceName
            val safeName = advName ?: dev.name ?: "(Unnamed)"

            val safeAddr = runCatching { dev.address }.getOrElse { "Permission required" }

            addLog("onScanResult: rssi=${result.rssi} $safeName $safeAddr")

            // De-dup by address if we have one
            runOnUiThread {
                val idx = when {
                    safeAddr != "Permission required" ->
                        deviceRows.indexOfFirst { it.address == safeAddr }
                    else ->
                        deviceRows.indexOfFirst { it.name == safeName }
                }

                if (idx >= 0) {
                    // Update name if it improved from "(Unnamed)" to something fr
                    val existing = deviceRows[idx]
                    val newName = if (existing.name == "(Unnamed)" && safeName != "(Unnamed)") safeName else existing.name
                    val newAddr = if (existing.address == "Permission required" && safeAddr != "Permission required") safeAddr else existing.address
                    if (newName != existing.name || newAddr != existing.address) {
                        deviceRows[idx] = existing.copy(name = newName, address = newAddr)
                    }
                } else {
                    deviceRows.add(DeviceRow(name = safeName, address = safeAddr))
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            addLog("onScanFailed: code=$errorCode") // 1=ALREADY_STARTED, 2=REGISTRATION_FAILED, 5=INTERNAL_ERROR, 6=FEATURE_UNSUPPORTED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permLauncher.launch(btPerms)
        } else {
            afterPermissionsGranted()
        }
    }

    private fun afterPermissionsGranted() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish(); return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || adapter.isEnabled) {
            setContent {
                Application(
                    devices = deviceRows,               // rows with names
                    onEnableBt = { promptEnableBluetooth() },
                    onScanToggle = { scanLeDevice() }
                )
            }
        } else {
            enableBluetoothLauncher.launch(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    // BLE helper functions
    private fun promptEnableBluetooth() {
        val adapter = bluetoothAdapter
        if (adapter != null && !adapter.isEnabled) {
            enableBluetoothLauncher.launch(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            Toast.makeText(this, "Bluetooth already enabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanLeDevice() {
        addLog(
            "scanLeDevice(): sdk=${Build.VERSION.SDK_INT} " +
                    "permScan=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) hasPerm(Manifest.permission.BLUETOOTH_SCAN) else true} " +
                    "permConn=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) hasPerm(Manifest.permission.BLUETOOTH_CONNECT) else true} " +
                    "btOn=${bluetoothAdapter?.isEnabled == true}"
        )
        if (!scanning) {
            deviceRows.clear()
            handler.postDelayed({
                scanning = false
                stopScanSafe()
            }, SCAN_PERIOD_MS)
            scanning = true
            startScanSafe()
        } else {
            scanning = false
            stopScanSafe()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtPermissions()) {
            addLog("startScanSafe: missing runtime perms (SCAN/CONNECT)")
            return
        }
        val scanner = bluetoothLeScanner
        if (scanner == null) {
            addLog("startScanSafe: bluetoothLeScanner == null (BT off? stack wedged?)")
            return
        }

        try {
            addLog("startScanSafe: startScan() requested")
            scanner.startScan(leScanCallback)
        } catch (e: SecurityException) {
            addLog("startScanSafe: SecurityException ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtPermissions()) {
            addLog("stopScanSafe: missing runtime perms")
            return
        }
        val scanner = bluetoothLeScanner ?: return
        try {
            addLog("stopScanSafe: stopScan()")
            scanner.stopScan(leScanCallback)
        } catch (e: SecurityException) {
            addLog("stopScanSafe: SecurityException ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtPermissions()) return
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }
}
