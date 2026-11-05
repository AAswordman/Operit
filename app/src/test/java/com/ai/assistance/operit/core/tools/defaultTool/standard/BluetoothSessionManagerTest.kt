package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION", "UNCHECKED_CAST")
class BluetoothSessionManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val gatt = Mockito.mock(BluetoothGatt::class.java)
    private val service = Mockito.mock(BluetoothGattService::class.java)
    private val characteristic = Mockito.mock(BluetoothGattCharacteristic::class.java)
    private val serviceUuid = UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb")
    private val characteristicUuid = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
    private val sessions = BluetoothSessionManager::class.java.getDeclaredField("bleSessions").apply {
        isAccessible = true
    }.get(null) as MutableMap<String, Any>
    private val callbacks = BluetoothSessionManager::class.java.getDeclaredField("writeCallbacks").apply {
        isAccessible = true
    }.get(null) as MutableMap<String, CompletableDeferred<Int>>
    private val completeWrite = BluetoothSessionManager::class.java.getDeclaredMethod(
        "completeWriteCallback",
        BluetoothGatt::class.java,
        BluetoothGattCharacteristic::class.java,
        Int::class.javaPrimitiveType
    ).apply { isAccessible = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        Mockito.`when`(gatt.getService(serviceUuid)).thenReturn(service)
        Mockito.`when`(service.getCharacteristic(characteristicUuid)).thenReturn(characteristic)
        Mockito.`when`(service.uuid).thenReturn(serviceUuid)
        Mockito.`when`(characteristic.service).thenReturn(service)
        Mockito.`when`(characteristic.uuid).thenReturn(characteristicUuid)
        Mockito.`when`(characteristic.properties).thenReturn(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
        Mockito.`when`(gatt.writeCharacteristic(characteristic)).thenReturn(true)

        val sessionClass = BluetoothSessionManager::class.java.declaredClasses.single { it.simpleName == "BleSession" }
        val constructor = sessionClass.declaredConstructors.single().apply { isAccessible = true }
        sessions["ble-test"] = constructor.newInstance(gatt, "test-address", CompletableDeferred(true), mutableListOf<Any>())
    }

    @After
    fun tearDown() {
        sessions.remove("ble-test")
        sessions.remove("ble-test-second")
        callbacks.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `no response only characteristic writes the complete eleven byte command`() = runTest(dispatcher) {
        val payload = byteArrayOf(0x51, 0x78, 0xA3.toByte(), 0, 1, 0, 0, 0, 0xFF.toByte(), 0, 0)
        val transfer = async {
            BluetoothSessionManager.writeBleCharacteristic("ble-test", serviceUuid, characteristicUuid, payload)
        }
        runCurrent()

        Mockito.verify(characteristic).writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val value = org.mockito.ArgumentCaptor.forClass(ByteArray::class.java)
        Mockito.verify(characteristic).value = value.capture()
        assertArrayEquals(payload, value.value)
        assertFalse(transfer.isCompleted)

        completeWrite.invoke(BluetoothSessionManager, gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
        runCurrent()
        assertEquals(11, transfer.await().bytesWritten)
        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun `initial MTU chunks wait for separate callbacks before reporting the whole transfer`() = runTest(dispatcher) {
        val payload = ByteArray(41) { it.toByte() }
        val transfer = async {
            BluetoothSessionManager.writeBleCharacteristic("ble-test", serviceUuid, characteristicUuid, payload)
        }
        runCurrent()
        val firstWaiter = callbacks.values.single()
        Mockito.verify(gatt).writeCharacteristic(characteristic)
        advanceTimeBy(100)
        runCurrent()
        Mockito.verify(gatt).writeCharacteristic(characteristic)

        completeWrite.invoke(BluetoothSessionManager, gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
        runCurrent()
        assertTrue(callbacks.isEmpty())
        advanceTimeBy(15)
        runCurrent()
        val secondWaiter = callbacks.values.single()
        assertNotSame(firstWaiter, secondWaiter)
        Mockito.verify(gatt, Mockito.times(2)).writeCharacteristic(characteristic)
        assertFalse(transfer.isCompleted)

        completeWrite.invoke(BluetoothSessionManager, gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
        runCurrent()
        advanceTimeBy(15)
        runCurrent()
        assertNotSame(secondWaiter, callbacks.values.single())
        assertFalse(transfer.isCompleted)
        val value = org.mockito.ArgumentCaptor.forClass(ByteArray::class.java)
        Mockito.verify(characteristic, Mockito.times(3)).value = value.capture()
        assertEquals(listOf(20, 20, 1), value.allValues.map { it.size })
        assertArrayEquals(payload, value.allValues.fold(ByteArray(0)) { bytes, chunk -> bytes + chunk })
        Mockito.verify(characteristic, Mockito.times(3)).writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        completeWrite.invoke(BluetoothSessionManager, gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
        runCurrent()
        assertEquals(41, transfer.await().bytesWritten)
        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun `successful MTU callback changes chunk limit and failed callback retains it`() = runTest(dispatcher) {
        // Call the real connection callback without constructing the Android stub superclass.
        val callbackClass = Class.forName(
            "${BluetoothSessionManager::class.java.name}\$connectBle\$2\$callback\$1"
        ).asSubclass(BluetoothGattCallback::class.java)
        val callback = Mockito.mock(callbackClass, Mockito.CALLS_REAL_METHODS)
        callbackClass.getDeclaredField("\$sessionId").apply { isAccessible = true }.set(callback, "ble-test")
        callback.onMtuChanged(gatt, 247, BluetoothGatt.GATT_SUCCESS)
        callback.onMtuChanged(gatt, 23, 3)
        val payload = ByteArray(489) { it.toByte() }
        val transfer = async {
            write(payload)
        }
        runCurrent()
        repeat(3) { finishChunk() }
        assertEquals(489, transfer.await().bytesWritten)
        assertChunks(payload, listOf(244, 244, 1))
        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun `empty one byte and exact boundary payloads each submit one write`() = runTest(dispatcher) {
        for (size in listOf(0, 1, 20)) {
            Mockito.clearInvocations(gatt, characteristic)
            val payload = ByteArray(size) { it.toByte() }
            val transfer = async { write(payload) }
            runCurrent()
            assertFalse(transfer.isCompleted)
            assertChunks(payload, listOf(size))
            finishChunk()
            assertEquals(size, transfer.await().bytesWritten)
            assertTrue(callbacks.isEmpty())
        }
    }

    @Test
    fun `write only keeps acknowledged mode and both properties select no response`() = runTest(dispatcher) {
        val payload = ByteArray(41) { it.toByte() }
        Mockito.`when`(characteristic.properties).thenReturn(BluetoothGattCharacteristic.PROPERTY_WRITE)
        val acknowledged = async { write(payload) }
        runCurrent()
        Mockito.verify(characteristic).writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        assertChunks(payload, listOf(41))
        finishChunk()
        assertEquals(41, acknowledged.await().bytesWritten)

        Mockito.clearInvocations(gatt, characteristic)
        Mockito.`when`(characteristic.properties).thenReturn(
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        )
        val noResponse = async { write(payload) }
        runCurrent()
        repeat(3) { finishChunk() }
        assertChunks(payload, listOf(20, 20, 1))
        Mockito.verify(characteristic, Mockito.times(3)).writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        assertEquals(41, noResponse.await().bytesWritten)
        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun `non writable characteristic is rejected before submitting GATT work`() = runTest(dispatcher) {
        Mockito.`when`(characteristic.properties).thenReturn(BluetoothGattCharacteristic.PROPERTY_READ)
        val result = runCatching { write(byteArrayOf(1)) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        Mockito.verify(gatt, Mockito.never()).writeCharacteristic(characteristic)
        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun `invalid MTU fails before submitting chunks and releases the mutex`() = runTest(dispatcher) {
        val session = sessions.getValue("ble-test")
        val mtu = session.javaClass.getDeclaredField("mtu").apply { isAccessible = true }
        for (invalidMtu in listOf(3, 2)) {
            mtu.setInt(session, invalidMtu)
            val result = async { runCatching { write(ByteArray(41)) } }
            runCurrent()
            assertTrue(result.await().exceptionOrNull() is IllegalStateException)
            Mockito.verify(gatt, Mockito.never()).writeCharacteristic(characteristic)
            assertIdle()
        }
        mtu.setInt(session, 23)
        assertNextWriteSucceeds()
    }

    @Test
    fun `initiation failure callback failure and timeout stop chunks and release waiter and mutex`() = runTest(dispatcher) {
        for (failure in listOf("initiation", "status", "timeout")) {
            Mockito.clearInvocations(gatt, characteristic)
            Mockito.`when`(gatt.writeCharacteristic(characteristic)).thenReturn(failure != "initiation")
            val transfer = async { runCatching { write(ByteArray(41)) } }
            runCurrent()
            when (failure) {
                "status" -> {
                    completeWrite.invoke(BluetoothSessionManager, gatt, characteristic, 3)
                    runCurrent()
                }
                "timeout" -> {
                    advanceTimeBy(5000)
                    runCurrent()
                }
            }
            val error = transfer.await().exceptionOrNull()
            assertTrue(error is IllegalStateException)
            when (failure) {
                "initiation" -> assertEquals("Failed to write BLE characteristic", error?.message)
                "status" -> assertEquals("BLE characteristic write failed: 3", error?.message)
                "timeout" -> assertEquals("BLE characteristic write timed out", error?.message)
            }
            advanceTimeBy(100)
            runCurrent()
            Mockito.verify(gatt).writeCharacteristic(characteristic)
            assertIdle()
            Mockito.`when`(gatt.writeCharacteristic(characteristic)).thenReturn(true)
            assertNextWriteSucceeds()
        }
    }

    @Test
    fun `cancellation while waiting and between chunks stops later submissions and releases mutex`() = runTest(dispatcher) {
        for (betweenChunks in listOf(false, true)) {
            Mockito.clearInvocations(gatt, characteristic)
            val transfer = async { write(ByteArray(41)) }
            runCurrent()
            if (betweenChunks) {
                completeWrite.invoke(BluetoothSessionManager, gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
                runCurrent()
                assertTrue(callbacks.isEmpty())
            }
            transfer.cancelAndJoin()
            advanceTimeBy(100)
            runCurrent()
            assertTrue(transfer.isCancelled)
            Mockito.verify(gatt).writeCharacteristic(characteristic)
            assertIdle()
            assertNextWriteSucceeds()
        }
    }

    @Test
    fun `same session concurrent writes cannot interleave chunks or replace waiters`() = runTest(dispatcher) {
        val firstPayload = ByteArray(41) { it.toByte() }
        val secondPayload = ByteArray(21) { (100 + it).toByte() }
        val first = async { write(firstPayload) }
        runCurrent()
        val firstWaiter = callbacks.values.single()
        val second = async { write(secondPayload) }
        runCurrent()
        assertTrue(firstWaiter === callbacks.values.single())
        Mockito.verify(gatt).writeCharacteristic(characteristic)
        repeat(2) {
            finishChunk()
            assertFalse(first.isCompleted)
            assertFalse(second.isCompleted)
        }
        finishChunk()
        assertEquals(41, first.await().bytesWritten)
        assertFalse(second.isCompleted)
        repeat(2) { finishChunk() }
        assertEquals(21, second.await().bytesWritten)
        assertChunks(firstPayload + secondPayload, listOf(20, 20, 1, 20, 1))
        assertIdle()
    }

    @Test
    fun `independent sessions keep chunks and callback waiters isolated`() = runTest(dispatcher) {
        val secondGatt = Mockito.mock(BluetoothGatt::class.java)
        val secondService = Mockito.mock(BluetoothGattService::class.java)
        val secondCharacteristic = Mockito.mock(BluetoothGattCharacteristic::class.java)
        // Identical UUIDs on separate connections must still have independent callback ownership.
        Mockito.`when`(secondGatt.getService(serviceUuid)).thenReturn(secondService)
        Mockito.`when`(secondService.getCharacteristic(characteristicUuid)).thenReturn(secondCharacteristic)
        Mockito.`when`(secondService.uuid).thenReturn(serviceUuid)
        Mockito.`when`(secondCharacteristic.service).thenReturn(secondService)
        Mockito.`when`(secondCharacteristic.uuid).thenReturn(characteristicUuid)
        Mockito.`when`(secondCharacteristic.properties).thenReturn(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
        Mockito.`when`(secondGatt.writeCharacteristic(secondCharacteristic)).thenReturn(true)
        val constructor = sessions.getValue("ble-test").javaClass.declaredConstructors.single().apply {
            isAccessible = true
        }
        sessions["ble-test-second"] = constructor.newInstance(
            secondGatt, "second-address", CompletableDeferred(true), mutableListOf<Any>()
        )

        val firstPayload = ByteArray(41) { it.toByte() }
        val secondPayload = ByteArray(21) { (100 + it).toByte() }
        val first = async { write(firstPayload) }
        runCurrent()
        val firstWaiter = callbacks.values.single()
        val second = async {
            BluetoothSessionManager.writeBleCharacteristic(
                "ble-test-second", serviceUuid, characteristicUuid, secondPayload
            )
        }
        runCurrent()
        assertEquals(2, callbacks.size)
        val secondWaiter = callbacks.values.single { it !== firstWaiter }
        Mockito.verify(gatt).writeCharacteristic(characteristic)
        Mockito.verify(secondGatt).writeCharacteristic(secondCharacteristic)

        completeWrite.invoke(BluetoothSessionManager, secondGatt, secondCharacteristic, BluetoothGatt.GATT_SUCCESS)
        runCurrent()
        assertTrue(firstWaiter === callbacks.values.single())
        assertFalse(firstWaiter.isCompleted)
        advanceTimeBy(15)
        runCurrent()
        assertEquals(2, callbacks.size)
        assertNotSame(secondWaiter, callbacks.values.single { it !== firstWaiter })
        assertFalse(second.isCompleted)
        completeWrite.invoke(BluetoothSessionManager, secondGatt, secondCharacteristic, BluetoothGatt.GATT_SUCCESS)
        runCurrent()
        assertEquals(21, second.await().bytesWritten)
        assertTrue(firstWaiter === callbacks.values.single())
        assertFalse(first.isCompleted)
        Mockito.verify(gatt).writeCharacteristic(characteristic)

        val secondValues = org.mockito.ArgumentCaptor.forClass(ByteArray::class.java)
        Mockito.verify(secondCharacteristic, Mockito.times(2)).value = secondValues.capture()
        assertEquals(listOf(20, 1), secondValues.allValues.map { it.size })
        assertArrayEquals(secondPayload, secondValues.allValues.fold(ByteArray(0)) { bytes, chunk -> bytes + chunk })
        Mockito.verify(secondGatt, Mockito.times(2)).writeCharacteristic(secondCharacteristic)

        repeat(3) { finishChunk() }
        assertEquals(41, first.await().bytesWritten)
        assertChunks(firstPayload, listOf(20, 20, 1))
        assertIdle()
        val secondSession = sessions.getValue("ble-test-second")
        val secondMutex = secondSession.javaClass.getDeclaredField("writeMutex").apply {
            isAccessible = true
        }.get(secondSession) as Mutex
        assertFalse(secondMutex.isLocked)
    }

    private suspend fun write(payload: ByteArray) = BluetoothSessionManager.writeBleCharacteristic(
        "ble-test", serviceUuid, characteristicUuid, payload
    )

    private fun TestScope.finishChunk() {
        completeWrite.invoke(BluetoothSessionManager, gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
        runCurrent()
        advanceTimeBy(15)
        runCurrent()
    }

    private fun assertChunks(payload: ByteArray, sizes: List<Int>) {
        val value = org.mockito.ArgumentCaptor.forClass(ByteArray::class.java)
        Mockito.verify(characteristic, Mockito.times(sizes.size)).value = value.capture()
        assertEquals(sizes, value.allValues.map { it.size })
        assertArrayEquals(payload, value.allValues.fold(ByteArray(0)) { bytes, chunk -> bytes + chunk })
        Mockito.verify(gatt, Mockito.times(sizes.size)).writeCharacteristic(characteristic)
    }

    private fun assertIdle() {
        assertTrue(callbacks.isEmpty())
        val session = sessions.getValue("ble-test")
        val mutex = session.javaClass.getDeclaredField("writeMutex").apply { isAccessible = true }.get(session) as Mutex
        assertFalse(mutex.isLocked)
    }

    private suspend fun TestScope.assertNextWriteSucceeds() {
        val transfer = async { write(byteArrayOf(1)) }
        runCurrent()
        finishChunk()
        assertEquals(1, transfer.await().bytesWritten)
        assertIdle()
    }
}
