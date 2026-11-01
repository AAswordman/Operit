package com.ai.assistance.operit.core.tools.builtin.media

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Base64
import com.ai.assistance.operit.core.tools.BluetoothBleCharacteristicData
import com.ai.assistance.operit.core.tools.BluetoothBleNotificationData
import com.ai.assistance.operit.core.tools.BluetoothBleNotificationEntry
import com.ai.assistance.operit.core.tools.BluetoothBleServiceData
import com.ai.assistance.operit.core.tools.BluetoothBleServicesData
import com.ai.assistance.operit.core.tools.BluetoothReadData
import com.ai.assistance.operit.core.tools.BluetoothScanResultData
import com.ai.assistance.operit.core.tools.BluetoothScannedDeviceData
import com.ai.assistance.operit.core.tools.BluetoothSessionData
import com.ai.assistance.operit.core.tools.BluetoothTransferData
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope

object BluetoothSessionManager {
    private const val DEFAULT_SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    private const val CLIENT_CHARACTERISTIC_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    private data class ClassicSession(
        val socket: BluetoothSocket,
        val input: InputStream,
        val output: OutputStream,
        val address: String
    )

    private data class ClassicListener(
        val serverSocket: BluetoothServerSocket,
        val name: String,
        val uuid: UUID
    )

    private data class BleSession(
        val gatt: BluetoothGatt,
        val address: String,
        val servicesReady: CompletableDeferred<Boolean>,
        val notifications: MutableList<BluetoothBleNotificationEntry>
    )

    private val classicSessions = ConcurrentHashMap<String, ClassicSession>()
    private val classicListeners = ConcurrentHashMap<String, ClassicListener>()
    private val bleSessions = ConcurrentHashMap<String, BleSession>()

    private fun newSessionId(prefix: String): String {
        return "$prefix-${UUID.randomUUID()}"
    }

    fun defaultSppUuid(): UUID = UUID.fromString(DEFAULT_SPP_UUID)

    fun parseUuid(value: String?): UUID {
        val normalized = value?.trim().orEmpty()
        return if (normalized.isBlank()) defaultSppUuid() else UUID.fromString(normalized)
    }

    fun adapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return null
        return manager.adapter
    }

    fun deviceTypeName(type: Int): String {
        return when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
            BluetoothDevice.DEVICE_TYPE_LE -> "le"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"
            BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "unknown"
            else -> "unknown"
        }
    }

    fun bondStateName(state: Int): String {
        return when (state) {
            BluetoothDevice.BOND_NONE -> "none"
            BluetoothDevice.BOND_BONDING -> "bonding"
            BluetoothDevice.BOND_BONDED -> "bonded"
            else -> "unknown"
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    suspend fun scan(
        context: Context,
        durationMs: Long,
        includeBle: Boolean
    ): BluetoothScanResultData = coroutineScope {
        val adapter = adapter(context) ?: throw IllegalStateException("Bluetooth is not supported")
        val devices = ConcurrentHashMap<String, BluetoothScannedDeviceData>()

        val classicJob = launch(Dispatchers.Main) {
            scanClassic(context, adapter, durationMs, devices)
        }
        val bleJob =
            if (includeBle) {
                launch(Dispatchers.Main) { scanBle(adapter, durationMs, devices) }
            } else {
                null
            }

        classicJob.join()
        bleJob?.join()

        BluetoothScanResultData(
            devices =
                devices.values.sortedWith(
                    compareBy<BluetoothScannedDeviceData> { it.name ?: "" }.thenBy { it.address }
                ),
            durationMs = durationMs,
            includesBle = includeBle
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun scanClassic(
        context: Context,
        adapter: BluetoothAdapter,
        durationMs: Long,
        devices: ConcurrentHashMap<String, BluetoothScannedDeviceData>
    ) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != BluetoothDevice.ACTION_FOUND) {
                        return
                    }
                    val device = getBluetoothDeviceExtra(intent)
                    if (device != null) {
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        devices[device.address] =
                            BluetoothScannedDeviceData(
                                name = device.name,
                                address = device.address,
                                type = deviceTypeName(device.type),
                                bondState = bondStateName(device.bondState),
                                source = "classic",
                                rssi = if (rssi == Short.MIN_VALUE) null else rssi.toInt()
                            )
                    }
                }
            }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        try {
            adapter.cancelDiscovery()
            adapter.startDiscovery()
            delay(durationMs)
        } finally {
            adapter.cancelDiscovery()
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanBle(
        adapter: BluetoothAdapter,
        durationMs: Long,
        devices: ConcurrentHashMap<String, BluetoothScannedDeviceData>
    ) {
        val scanner = adapter.bluetoothLeScanner ?: return
        val callback =
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    devices[device.address] =
                        BluetoothScannedDeviceData(
                            name = device.name,
                            address = device.address,
                            type = deviceTypeName(device.type),
                            bondState = bondStateName(device.bondState),
                            source = "ble",
                            rssi = result.rssi
                        )
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onScanResult(0, it) }
                }
            }
        try {
            scanner.startScan(callback)
            delay(durationMs)
        } finally {
            scanner.stopScan(callback)
        }
    }

    @Suppress("DEPRECATION")
    private fun getBluetoothDeviceExtra(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    suspend fun connectClassic(
        context: Context,
        address: String,
        uuid: UUID
    ): BluetoothSessionData = withContext(Dispatchers.IO) {
        val adapter = adapter(context) ?: throw IllegalStateException("Bluetooth is not supported")
        val device = adapter.getRemoteDevice(address)
        adapter.cancelDiscovery()
        val socket = device.createRfcommSocketToServiceRecord(uuid)
        socket.connect()
        val sessionId = newSessionId("bt")
        classicSessions[sessionId] =
            ClassicSession(
                socket = socket,
                input = socket.inputStream,
                output = socket.outputStream,
                address = address
            )
        BluetoothSessionData(sessionId = sessionId, address = address, mode = "classic")
    }

    @SuppressLint("MissingPermission")
    fun listenClassic(
        context: Context,
        name: String,
        uuid: UUID
    ): BluetoothSessionData {
        val adapter = adapter(context) ?: throw IllegalStateException("Bluetooth is not supported")
        val serverSocket = adapter.listenUsingRfcommWithServiceRecord(name, uuid)
        val sessionId = newSessionId("bt-listen")
        classicListeners[sessionId] = ClassicListener(serverSocket, name, uuid)
        return BluetoothSessionData(sessionId = sessionId, address = "local", mode = "classic_listener")
    }

    @SuppressLint("MissingPermission")
    suspend fun acceptClassic(listenerId: String, timeoutMs: Int): BluetoothSessionData =
        withContext(Dispatchers.IO) {
            val listener = classicListeners[listenerId]
                ?: throw IllegalArgumentException("Bluetooth listener session not found: $listenerId")
            val socket = listener.serverSocket.accept(timeoutMs)
            val address = socket.remoteDevice.address
            val sessionId = newSessionId("bt")
            classicSessions[sessionId] =
                ClassicSession(
                    socket = socket,
                    input = socket.inputStream,
                    output = socket.outputStream,
                    address = address
                )
            BluetoothSessionData(sessionId = sessionId, address = address, mode = "classic")
        }

    suspend fun sendClassic(sessionId: String, bytes: ByteArray): BluetoothTransferData =
        withContext(Dispatchers.IO) {
            val session = classicSessions[sessionId]
                ?: throw IllegalArgumentException("Bluetooth session not found: $sessionId")
            session.output.write(bytes)
            session.output.flush()
            BluetoothTransferData(sessionId = sessionId, bytesWritten = bytes.size)
        }

    suspend fun readClassic(sessionId: String, maxBytes: Int, timeoutMs: Long): BluetoothReadData =
        withContext(Dispatchers.IO) {
            val session = classicSessions[sessionId]
                ?: throw IllegalArgumentException("Bluetooth session not found: $sessionId")
            val buffer = ByteArray(maxBytes)
            val deadline = System.currentTimeMillis() + timeoutMs
            var count = 0
            while (count == 0 && System.currentTimeMillis() < deadline) {
                val available = session.input.available()
                if (available > 0) {
                    count = session.input.read(buffer, 0, minOf(maxBytes, available))
                } else {
                    delay(20)
                }
            }
            val bytes = if (count > 0) buffer.copyOf(count) else ByteArray(0)
            BluetoothReadData(
                sessionId = sessionId,
                bytesRead = bytes.size,
                text = bytes.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8),
                dataBase64 = bytes.takeIf { it.isNotEmpty() }?.let { encodeBase64(it) }
            )
        }

    suspend fun sendAndReadClassic(
        sessionId: String,
        bytes: ByteArray,
        maxBytes: Int,
        timeoutMs: Long
    ): BluetoothReadData {
        sendClassic(sessionId, bytes)
        return readClassic(sessionId, maxBytes, timeoutMs)
    }

    fun closeClassic(sessionId: String) {
        classicSessions.remove(sessionId)?.socket?.close()
        classicListeners.remove(sessionId)?.serverSocket?.close()
    }

    @SuppressLint("MissingPermission")
    suspend fun connectBle(
        context: Context,
        address: String,
        autoConnect: Boolean
    ): BluetoothSessionData = withContext(Dispatchers.Main) {
        val adapter = adapter(context) ?: throw IllegalStateException("Bluetooth is not supported")
        val device = adapter.getRemoteDevice(address)
        val sessionId = newSessionId("ble")
        val servicesReady = CompletableDeferred<Boolean>()
        val notifications = mutableListOf<BluetoothBleNotificationEntry>()
        val callback =
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices()
                    } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                        servicesReady.complete(false)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    servicesReady.complete(status == BluetoothGatt.GATT_SUCCESS)
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        completeReadCallback(gatt, characteristic, characteristic.value ?: ByteArray(0))
                    }
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        completeReadCallback(gatt, characteristic, value)
                    }
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    completeWriteCallback(gatt, characteristic, status)
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    appendNotification(notifications, characteristic, characteristic.value ?: ByteArray(0))
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    appendNotification(notifications, characteristic, value)
                }
            }

        val gatt =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, autoConnect, callback)
            }
        bleSessions[sessionId] = BleSession(gatt, address, servicesReady, notifications)
        BluetoothSessionData(sessionId = sessionId, address = address, mode = "ble")
    }

    @SuppressLint("MissingPermission")
    suspend fun discoverBleServices(sessionId: String, timeoutMs: Long): BluetoothBleServicesData {
        val session = bleSessions[sessionId]
            ?: throw IllegalArgumentException("BLE session not found: $sessionId")
        val ready = withTimeoutOrNull(timeoutMs) { session.servicesReady.await() } == true
        if (!ready) {
            throw IllegalStateException("BLE services were not discovered before timeout")
        }
        val services =
            session.gatt.services.map { service ->
                BluetoothBleServiceData(
                    uuid = service.uuid.toString(),
                    characteristics =
                        service.characteristics.map { characteristic ->
                            BluetoothBleCharacteristicData(
                                uuid = characteristic.uuid.toString(),
                                properties = characteristicProperties(characteristic.properties)
                            )
                        }
                )
            }
        return BluetoothBleServicesData(sessionId = sessionId, services = services)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    suspend fun readBleCharacteristic(
        sessionId: String,
        serviceUuid: UUID,
        characteristicUuid: UUID,
        timeoutMs: Long
    ): BluetoothReadData {
        val session = bleSessions[sessionId]
            ?: throw IllegalArgumentException("BLE session not found: $sessionId")
        val characteristic = findCharacteristic(session.gatt, serviceUuid, characteristicUuid)
        val deferred = CompletableDeferred<ByteArray>()
        val callback = BleReadCallback(session.gatt, characteristic, deferred)
        callback.install()
        try {
            if (!session.gatt.readCharacteristic(characteristic)) {
                throw IllegalStateException("Failed to start BLE characteristic read")
            }
            // 超时和成功读取空内容是两种结果，超时必须进入工具失败路径。
            val bytes = withTimeoutOrNull(timeoutMs) { deferred.await() }
                ?: throw IllegalStateException("BLE characteristic read timed out")
            return BluetoothReadData(
                sessionId = sessionId,
                bytesRead = bytes.size,
                text = bytes.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8),
                dataBase64 = bytes.takeIf { it.isNotEmpty() }?.let { encodeBase64(it) }
            )
        } finally {
            callback.uninstall()
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    suspend fun writeBleCharacteristic(
        sessionId: String,
        serviceUuid: UUID,
        characteristicUuid: UUID,
        bytes: ByteArray
    ): BluetoothTransferData {
        val session = bleSessions[sessionId]
            ?: throw IllegalArgumentException("BLE session not found: $sessionId")
        val characteristic = findCharacteristic(session.gatt, serviceUuid, characteristicUuid)
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<Int>()
            val callback = BleWriteCallback(session.gatt, characteristic, deferred)
            callback.install()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val status = session.gatt.writeCharacteristic(
                        characteristic,
                        bytes,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        throw IllegalStateException("Failed to write BLE characteristic: $status")
                    }
                } else {
                    characteristic.value = bytes
                    if (!session.gatt.writeCharacteristic(characteristic)) {
                        throw IllegalStateException("Failed to write BLE characteristic")
                    }
                }
                val callbackStatus = withTimeoutOrNull(5000L) { deferred.await() }
                    ?: throw IllegalStateException("BLE characteristic write timed out")
                if (callbackStatus != BluetoothGatt.GATT_SUCCESS) {
                    throw IllegalStateException("BLE characteristic write failed: $callbackStatus")
                }
                BluetoothTransferData(sessionId = sessionId, bytesWritten = bytes.size)
            } finally {
                callback.uninstall()
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun subscribeBleCharacteristic(
        sessionId: String,
        serviceUuid: UUID,
        characteristicUuid: UUID,
        enable: Boolean
    ): BluetoothTransferData {
        val session = bleSessions[sessionId]
            ?: throw IllegalArgumentException("BLE session not found: $sessionId")
        val characteristic = findCharacteristic(session.gatt, serviceUuid, characteristicUuid)
        if (!session.gatt.setCharacteristicNotification(characteristic, enable)) {
            throw IllegalStateException("Failed to update BLE notification state")
        }
        val descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG_UUID))
        if (descriptor != null) {
            descriptor.value =
                if (enable) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
            session.gatt.writeDescriptor(descriptor)
        }
        return BluetoothTransferData(sessionId = sessionId, bytesWritten = 0)
    }

    fun readBleNotifications(sessionId: String, limit: Int): BluetoothBleNotificationData {
        val session = bleSessions[sessionId]
            ?: throw IllegalArgumentException("BLE session not found: $sessionId")
        val items =
            synchronized(session.notifications) {
                val selected = session.notifications.take(limit)
                session.notifications.subList(0, selected.size).clear()
                selected
            }
        return BluetoothBleNotificationData(sessionId = sessionId, notifications = items)
    }

    fun closeBle(sessionId: String) {
        bleSessions.remove(sessionId)?.gatt?.close()
    }

    fun closeAny(sessionId: String) {
        closeClassic(sessionId)
        closeBle(sessionId)
    }

    private fun findCharacteristic(
        gatt: BluetoothGatt,
        serviceUuid: UUID,
        characteristicUuid: UUID
    ): BluetoothGattCharacteristic {
        val service: BluetoothGattService =
            gatt.getService(serviceUuid)
                ?: throw IllegalArgumentException("BLE service not found: $serviceUuid")
        return service.getCharacteristic(characteristicUuid)
            ?: throw IllegalArgumentException("BLE characteristic not found: $characteristicUuid")
    }

    private fun characteristicProperties(properties: Int): List<String> {
        val names = mutableListOf<String>()
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) names += "read"
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) names += "write"
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) names += "write_no_response"
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) names += "notify"
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) names += "indicate"
        return names
    }

    fun decodePayload(text: String?, dataBase64: String?): ByteArray {
        if (dataBase64 != null) {
            return Base64.decode(dataBase64, Base64.DEFAULT)
        }
        return text.orEmpty().toByteArray(Charsets.UTF_8)
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private class BleReadCallback(
        private val gatt: BluetoothGatt,
        private val characteristic: BluetoothGattCharacteristic,
        private val deferred: CompletableDeferred<ByteArray>
    ) {
        fun install() {
            readCallbacks[readCallbackKey(gatt, characteristic)] = deferred
        }

        fun uninstall() {
            readCallbacks.remove(readCallbackKey(gatt, characteristic))
        }
    }

    private class BleWriteCallback(
        private val gatt: BluetoothGatt,
        private val characteristic: BluetoothGattCharacteristic,
        private val deferred: CompletableDeferred<Int>
    ) {
        fun install() {
            writeCallbacks[readCallbackKey(gatt, characteristic)] = deferred
        }

        fun uninstall() {
            writeCallbacks.remove(readCallbackKey(gatt, characteristic))
        }
    }

    private val readCallbacks = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()
    private val writeCallbacks = ConcurrentHashMap<String, CompletableDeferred<Int>>()

    private fun completeReadCallback(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        readCallbacks[readCallbackKey(gatt, characteristic)]?.complete(value)
    }

    private fun completeWriteCallback(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        writeCallbacks[readCallbackKey(gatt, characteristic)]?.complete(status)
    }

    private fun appendNotification(
        notifications: MutableList<BluetoothBleNotificationEntry>,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        synchronized(notifications) {
            notifications +=
                BluetoothBleNotificationEntry(
                    characteristicUuid = characteristic.uuid.toString(),
                    bytesRead = value.size,
                    text = value.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8),
                    dataBase64 = value.takeIf { it.isNotEmpty() }?.let { encodeBase64(it) }
                )
        }
    }

    private fun readCallbackKey(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): String {
        return "${System.identityHashCode(gatt)}:${characteristic.service.uuid}:${characteristic.uuid}"
    }
}
