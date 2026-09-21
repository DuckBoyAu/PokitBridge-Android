package com.duckboyau.pokitbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class BridgeService : Service() {
    inner class LocalBinder : Binder() {
        fun getService(): BridgeService = this@BridgeService
    }

    private val binder = LocalBinder()
    private val listeners = mutableListOf<(String) -> Unit>()

    private var adapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gatt: BluetoothGatt? = null
    private var server: BluetoothGattServer? = null
    private var mmSettingsRemote: BluetoothGattCharacteristic? = null
    private var mmReadingLocal: BluetoothGattCharacteristic? = null
    private var statusLocal: BluetoothGattCharacteristic? = null
    private var previousName: String? = null
    private var lastReading = ByteArray(7)
    private var lastStatus = ByteArray(8)
    private val notifyDevices = mutableSetOf<BluetoothDevice>()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pokit Bridge", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Pokit Bridge")
            .setContentText("Meter proxy running")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        adapter = getSystemService(BluetoothManager::class.java).adapter
        startPipeline()
    }

    override fun onDestroy() {
        stopPipeline()
        super.onDestroy()
    }

    fun addLogListener(fn: (String) -> Unit) {
        listeners.add(fn)
    }

    private fun log(msg: String) {
        listeners.forEach { it(msg) }
    }

    private fun startPipeline() {
        val a = adapter ?: run {
            log("No Bluetooth adapter")
            return
        }
        previousName = a.name
        scanner = a.bluetoothLeScanner
        advertiser = a.bluetoothLeAdvertiser
        log("Scanning for real Pokit Pro…")
        scanner?.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback
        )
    }

    private fun stopPipeline() {
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        try { advertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        previousName?.let { adapter?.name = it }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (gatt != null) return
            val name = result.device.name ?: result.scanRecord?.deviceName ?: ""
            val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
            val looksPokit = name.contains("Pokit", ignoreCase = true) ||
                uuids.contains(PokitUuids.STATUS_SERVICE) ||
                uuids.contains(PokitUuids.MM_SERVICE)
            if (!looksPokit) return
            log("Seen $name rssi=${result.rssi} ${result.device.address}")
            scanner?.stopScan(this)
            log("Connecting to real meter ${result.device.address}")
            gatt = result.device.connectGatt(this@BridgeService, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            log("Scan failed $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Real meter connected, discovering services")
                g.requestMtu(64)
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Real meter disconnected ($status)")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val mm = g.getService(PokitUuids.MM_SERVICE)
            val st = g.getService(PokitUuids.STATUS_SERVICE)
            if (mm == null) {
                log("No MM service on this device — not a Pokit?")
                g.disconnect()
                return
            }
            mmSettingsRemote = mm.getCharacteristic(PokitUuids.MM_SETTINGS)
            val reading = mm.getCharacteristic(PokitUuids.MM_READING)
            enableNotify(g, reading)
            st?.getCharacteristic(PokitUuids.STATUS_CHAR)?.let { enableNotify(g, it) }
            val settings = byteArrayOf(1, 255.toByte(), 200.toByte(), 0, 0, 0)
            mmSettingsRemote?.let {
                it.value = settings
                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                g.writeCharacteristic(it)
            }
            log("Subscribed. Starting Pokit emulator advertise")
            startEmulator()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value ?: return
            when (c.uuid) {
                PokitUuids.MM_READING -> {
                    lastReading = v.copyOf()
                    mmReadingLocal?.value = lastReading
                    notifySubscribers(mmReadingLocal)
                    if (v.size >= 7) {
                        val f = ByteBuffer.wrap(v, 1, 4).order(ByteOrder.LITTLE_ENDIAN).float
                        val mode = v[5].toInt() and 0xFF
                        val st = v[0].toInt() and 0xFF
                        WatchBridge.post(f, mode, st)
                        log("MM ${"%.4g".format(f.toDouble())} mode=$mode")
                    }
                }
                PokitUuids.STATUS_CHAR -> {
                    lastStatus = v.copyOf()
                    statusLocal?.value = lastStatus
                    notifySubscribers(statusLocal)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            log("MTU $mtu")
        }
    }

    private fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic?) {
        c ?: return
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(PokitUuids.CCCD) ?: return
        d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        g.writeDescriptor(d)
    }

    private fun startEmulator() {
        val mgr = getSystemService(BluetoothManager::class.java)
        server = mgr.openGattServer(this, serverCallback)
        val s = server ?: run {
            log("openGattServer failed")
            return
        }

        val mm = BluetoothGattService(PokitUuids.MM_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val settings = BluetoothGattCharacteristic(
            PokitUuids.MM_SETTINGS,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        mmReadingLocal = BluetoothGattCharacteristic(
            PokitUuids.MM_READING,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).also {
            it.addDescriptor(BluetoothGattDescriptor(PokitUuids.CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
            it.value = lastReading
            mm.addCharacteristic(it)
        }
        mm.addCharacteristic(settings)

        val statusSvc = BluetoothGattService(PokitUuids.STATUS_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        statusLocal = BluetoothGattCharacteristic(
            PokitUuids.STATUS_CHAR,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).also {
            it.addDescriptor(BluetoothGattDescriptor(PokitUuids.CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
            it.value = lastStatus
            statusSvc.addCharacteristic(it)
        }

        val dis = BluetoothGattService(PokitUuids.DIS_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        fun ro(uuid: UUID, text: String) = BluetoothGattCharacteristic(
            uuid, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ
        ).also { it.value = text.toByteArray() }
        dis.addCharacteristic(ro(PokitUuids.DIS_MANUFACTURER, "Pokit Innovations"))
        dis.addCharacteristic(ro(PokitUuids.DIS_MODEL, "Pokit Pro"))
        dis.addCharacteristic(ro(PokitUuids.DIS_FIRMWARE, "1.0.0"))

        s.addService(dis)
        s.addService(statusSvc)
        s.addService(mm)

        try {
            adapter?.name = PokitUuids.ADVERT_NAME
        } catch (_: Exception) {
        }

        val settingsAdv = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(PokitUuids.STATUS_SERVICE))
            .build()
        advertiser?.startAdvertising(settingsAdv, data, scanResponse, advertiseCallback)
        log("Advertising as ${PokitUuids.ADVERT_NAME} (name + status UUID in scan response).")
        log("This phone cannot see its own ads. Official Pokit on THIS Pixel will not find it.")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            log("Advertise started")
        }
        override fun onStartFailure(errorCode: Int) {
            val why = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "data too large"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
                ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "internal"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "unsupported"
                else -> "code $errorCode"
            }
            log("Advertise failed: $why")
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            log("Emulator client ${device.address} state=$newState status=$status")
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                notifyDevices.remove(device)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic
        ) {
            val value = when (characteristic.uuid) {
                PokitUuids.MM_READING -> lastReading
                PokitUuids.STATUS_CHAR -> lastStatus
                else -> characteristic.value ?: ByteArray(0)
            }
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            log("Official app write ${characteristic.uuid} ${value.size}b")
            if (characteristic.uuid == PokitUuids.MM_SETTINGS) {
                mmSettingsRemote?.let {
                    it.value = value
                    it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    gatt?.writeCharacteristic(it)
                }
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            descriptor.value = value
            if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                notifyDevices.add(device)
                log("Notify enabled for ${device.address}")
            } else {
                notifyDevices.remove(device)
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    private fun notifySubscribers(c: BluetoothGattCharacteristic?) {
        c ?: return
        notifyDevices.forEach { server?.notifyCharacteristicChanged(it, c, false) }
    }

    companion object {
        private const val CHANNEL = "pokit_bridge"
        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, BridgeService::class.java))
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BridgeService::class.java))
        }
    }
}
