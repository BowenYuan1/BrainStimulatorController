package com.example.brainstimulatorcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES
import android.content.Intent
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
import androidx.core.content.ContextCompat
import com.example.brainstimulatorcontroller.ui.Application
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.android.awaitFrame
import java.util.*

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
        // Add ACCESS_FINE_LOCATION if needed for some devices
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
            // Continue to UI either way, user can enable later
            setContent { Application(
                devices = devices,
                onEnableBt = { promptEnableBluetooth() },
                onScanToggle = { scanLeDevice() }
            ) }
        }

    // Scan state and list exposed to UI
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD_MS = 10_000L


    private val devices = mutableStateListOf<BluetoothDevice>()

    private var bluetoothGatt: BluetoothGatt? = null
    private val gattCallback = object : BluetoothGattCallback() { /* add when ready */ }

    private val leScanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = result.scanRecord?.deviceName ?: dev.name ?: "(Unnamed)"
            val addr = runCatching { dev.address }.getOrElse { "NoAddr(perm?)" }
            addLog("onScanResult: rssi=${result.rssi} $name $addr")


            val already = if (addr.startsWith("NoAddr")) {
                devices.any { System.identityHashCode(it) == System.identityHashCode(dev) }
            } else {
                devices.any { runCatching { it.address }.getOrNull() == addr }
            }
            if (!already) runOnUiThread { devices.add(dev) }
        }

        override fun onScanFailed(errorCode: Int) {
            addLog("onScanFailed: code=$errorCode") // 1=ALREADY_STARTED, 2=REGISTRATION_FAILED, 5=INTERNAL_ERROR, 6=FEATURE_UNSUPPORTED
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init BT
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
        // Only read isEnabled after we have CONNECT perm on Android 12+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || adapter.isEnabled) {
            setContent { Application(
                devices = devices,
                onEnableBt = { promptEnableBluetooth() },
                onScanToggle = { scanLeDevice() }
            ) }
        } else {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        }
    }

    // BLE Helper functions
    private fun promptEnableBluetooth() {
        val adapter = bluetoothAdapter
        if (adapter != null && !adapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
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
            // clear list and schedule auto-stop
            devices.clear()
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