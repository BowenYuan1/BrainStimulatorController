package com.example.brainstimulatorcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.mutableStateListOf
import java.util.Date
import java.util.Locale
import androidx.core.content.ContextCompat
import com.example.brainstimulatorcontroller.ui.Application
import java.util.UUID


private const val CMD_SET: UByte   = 0x01u
private const val CMD_START: UByte = 0x02u
private const val CMD_STOP: UByte  = 0x03u

// Packet framing
private const val START_BYTE: UByte = 0xAAu
private const val END_BYTE: UByte   = 0x55u

private val SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
private val TX_CHARACTERISTIC_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
private val devicesByAddr = mutableMapOf<String, BluetoothDevice>()
private const val PAYLOAD_LEN_BYTES = 8

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
    val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    private companion object { const val TAG = "BT_SCAN" }

    private fun addLog(msg: String) = Log.d(TAG, msg)
    private fun hasPerm(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private val uiLogs = mutableStateListOf<String>()
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun uiLog(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        runOnUiThread {
            // prepend newest
            uiLogs.add(0, msg)
            if (uiLogs.size > 200) uiLogs.removeLast()
        }
    }
    private val btPerms = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val prev = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                val new = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                Log.d("BLE_BOND", "Bond state changed: $prev -> $new for ${device?.address}")
            }
        }
    }

    private fun hasBtPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btPerms.all { p ->
                ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
            }
        } else true
    }
    private fun makePayload(channel: Int, currentMA: Float, freqHz: Int): Int {
        val ch = channel.coerceIn(0, 255)
        val i10 = (currentMA * 10f).toInt().coerceIn(0, 255)
        val f   = freqHz.coerceIn(0, 0xFFFF)
        return (ch and 0xFF) or
                ((i10 and 0xFF) shl 8) or
                ((f and 0xFFFF) shl 16)
    }

    private fun findTxCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        // Preferred path: known service/char UUIDs
        gatt.getService(SERVICE_UUID)?.getCharacteristic(TX_CHARACTERISTIC_UUID)?.let { return it }

        // Fallback: search any service for a writable characteristic
        for (svc in gatt.services) {
            for (ch in svc.characteristics) {
                val props = ch.properties
                val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                val canWriteNoRsp = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                if (canWrite || canWriteNoRsp) return ch
            }
        }
        return null
    }

    private fun makePayload64(phase: Int, currentMA: Float, freqHz: Int): ByteArray {
        val out = ByteArray(8)

        // Scale and constrain to field widths
        val ph21  = phase and ((1 shl 21) - 1)          // 21 bits
        val fr22  = freqHz and ((1 shl 22) - 1)         // 22 bits
        val cur21 = ((currentMA * 10f).toInt()) and ((1 shl 21) - 1) // 21 bits current in 0.1 mA

        // Pack into a 64-bit big-endian value
        val packed: Long =
            (ph21.toLong() shl 43) or    // bits 63..43
                    (fr22.toLong() shl 21) or    // bits 42..21
                    cur21.toLong()               // bits 20..0

        // Emit as 8 bytes MSB first
        for (i in 0 until 8) {
            val shift = (7 - i) * 8
            out[i] = ((packed ushr shift) and 0xFF).toByte()
        }

        return out
    }

    private fun makeBlankPayload64(channel: Int): ByteArray {
        val out = ByteArray(PAYLOAD_LEN_BYTES)
        out[0] = (channel and 0xFF).toByte()
        return out
    }



    @SuppressLint("MissingPermission")
    private fun writeToPeripheral(bytes: ByteArray): Any {
        val gatt = bluetoothGatt ?: run {
            Log.w("BLE_GATT", "Not connected (bluetoothGatt == null)")
            return false
        }
        val ch = findTxCharacteristic(gatt) ?: run {
            Log.w("BLE_GATT", "TX characteristic not found")
            return false
        }

        // Prepend 0xAA as a start marker for the FPGA
        val framed = ByteArray(bytes.size + 1)
        framed[0] = 0xAA.toByte()
        System.arraycopy(bytes, 0, framed, 1, bytes.size)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.d("BLE_TX", "TX ${framed.size}B: " +
                        framed.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })

                val writeType =
                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

                val ok = gatt.writeCharacteristic(ch, framed, writeType)
                Log.d("BLE_GATT", "writeCharacteristic(T+) ok=$ok len=${framed.size}")
                ok
            } else {
                Log.d("BLE_TX", "TX ${framed.size}B: " +
                        framed.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })

                ch.writeType =
                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

                ch.value = framed
                val ok = gatt.writeCharacteristic(ch)
                Log.d("BLE_GATT", "writeCharacteristic legacy ok=$ok len=${framed.size}")
                ok
            }
        } catch (t: Throwable) {
            Log.e("BLE_GATT", "Write failed: ${t.message}", t)
            false
        } as Boolean
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) afterPermissionsGranted()
            else {
                Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            setContent {
                Application(
                    devices = deviceRows,
                    onEnableBt = { promptEnableBluetooth() },
                    onScanToggle = { scanLeDevice() },
                    onDeviceClick = { row ->
                        devicesByAddr[row.address]?.let {
                            connectToDevice(it)
                            uiLog("Connecting to ${row.name}")
                        }
                    },
                    onSendSet = { phase, ma, hz ->
                        sendSetCommand(phase, ma, hz)
                        uiLog("Send SET(Modify) phase=$phase I=${"%.1f".format(ma)}mA f=$hz Hz")
                    },
                    onStart = { phase, ma, hz ->
                        sendStartCommand(phase, ma, hz)
                        uiLog("Send START phase=$phase I=${"%.1f".format(ma)}mA f=$hz Hz")
                    },
                    onStop = { phase ->
                        sendStopCommand(phase)
                        uiLog("Send STOP")
                    },
                    logs = uiLogs
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
                if (safeAddr != "Permission required") {
                    devicesByAddr[safeAddr] = dev
                }

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
    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE_GATT", "Connected to ${gatt.device.address}")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE_GATT", "Disconnected from ${gatt.device.address}")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (service in gatt.services) {
                    Log.d("BLE_GATT", "Service: ${service.uuid}")
                    for (chara in service.characteristics) {
                        Log.d("BLE_GATT", "  Char: ${chara.uuid}")
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun afterPermissionsGranted() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish(); return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || adapter.isEnabled) {
            setContent {
                Application(
                    devices = deviceRows,
                    onEnableBt = { promptEnableBluetooth() },
                    onScanToggle = { scanLeDevice() },
                    onDeviceClick = { row ->
                        devicesByAddr[row.address]?.let { connectToDevice(it)
                            uiLog("Connecting to ${row.name}")
                        }
                    },
                    onSendSet = { phase, ma, hz ->
                        sendSetCommand(phase, ma, hz)
                        uiLog("Send SET(Modify) phase=$phase I=${"%.1f".format(ma)}mA f=$hz Hz")
                    },
                    onStart = { phase, ma, hz ->
                        sendStartCommand(phase, ma, hz)
                        uiLog("Send START phase=$phase I=${"%.1f".format(ma)}mA f=$hz Hz")
                    },
                    onStop = { phase ->
                        sendStopCommand(phase)
                        uiLog("Send STOP")
                    },
                    logs = uiLogs,
                )
            }
        } else {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    // BLE helper functions
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun promptEnableBluetooth() {
        val adapter = bluetoothAdapter
        if (adapter != null && !adapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
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

    fun sendSetCommand(channel: Int, currentMA: Float, freqHz: Int) {
        val payload = makePayload64(channel, currentMA, freqHz)
        val pkt = buildPacket(CMD_SET, channel, payload)
        writeToPeripheral(pkt)
    }

    fun sendStartCommand(channel: Int, ma: Float, hz: Int) {
        val payload = makeBlankPayload64(channel)
        val pkt = buildPacket(CMD_START, channel, payload)
        writeToPeripheral(pkt)
    }

    fun sendStopCommand(channel: Int) {
        val payload = makeBlankPayload64(channel)
        val pkt = buildPacket(CMD_STOP, channel, payload)
        writeToPeripheral(pkt)
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
        registerReceiver(bondReceiver, filter)
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
        unregisterReceiver(bondReceiver)
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtPermissions()) return
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }
}
