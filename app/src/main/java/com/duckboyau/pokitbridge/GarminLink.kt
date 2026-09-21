package com.duckboyau.pokitbridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQDevice.IQDeviceStatus

object GarminLink {
    private const val APP_ID = "5164386326c4474bbae2c033350ba7cb"
    private val watchApp = IQApp(APP_ID)
    private val handler = Handler(Looper.getMainLooper())
    private var iq: ConnectIQ? = null
    private var ready = false
    private var device: IQDevice? = null
    @Volatile private var sending = false
    @Volatile private var sendStartedAt = 0L
    @Volatile private var pending: HashMap<String, Any>? = null
    @Volatile private var lastSentKey = ""
    private var tickerStarted = false

    private val tick = object : Runnable {
        override fun run() {
            pump()
            handler.postDelayed(this, 40)
        }
    }

    fun init(ctx: Context) {
        if (iq == null) {
            val inst = ConnectIQ.getInstance(ctx, ConnectIQ.IQConnectType.WIRELESS)
            iq = inst
            inst.initialize(ctx, false, object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() {
                    ready = true
                    refreshDevice()
                    ScrapeHub.log("Garmin link ready: ${device?.friendlyName ?: "no watch yet"}")
                    probeApp()
                }
                override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                    ready = false
                    ScrapeHub.log("Garmin link error: ${errStatus.name}")
                }
                override fun onSdkShutDown() {
                    ready = false
                }
            })
        }
        if (!tickerStarted) {
            tickerStarted = true
            handler.post(tick)
        }
    }

    fun send(value: Float, mode: Int, status: Int, display: String?) {
        pending = hashMapOf(
            "v" to value.toDouble(),
            "m" to mode,
            "s" to status,
            "d" to (display ?: "")
        )
    }

    private fun pump() {
        val inst = iq ?: return
        if (!ready) return
        if (sending && System.currentTimeMillis() - sendStartedAt > 250) {
            sending = false
        }
        if (sending) return
        if (device == null) refreshDevice()
        val dev = device ?: return
        val payload = pending ?: return
        val key = "${payload["d"]}|${payload["v"]}|${payload["m"]}"
        val now = System.currentTimeMillis()
        if (key == lastSentKey && now - sendStartedAt < 350) return
        sending = true
        sendStartedAt = now
        lastSentKey = key
        try {
            inst.sendMessage(dev, watchApp, payload) { _, _, st ->
                sending = false
                if (st != ConnectIQ.IQMessageStatus.SUCCESS) {
                    lastSentKey = ""
                    ScrapeHub.log("Watch send ${st.name}")
                }
            }
        } catch (e: Exception) {
            sending = false
            lastSentKey = ""
            ScrapeHub.log("Watch send ${e.message}")
        }
    }

    private fun refreshDevice() {
        try {
            val all = iq?.getKnownDevices() ?: return
            device = all.firstOrNull { it.status == IQDeviceStatus.CONNECTED } ?: all.firstOrNull()
        } catch (_: Exception) {
        }
    }

    private fun probeApp() {
        val inst = iq ?: return
        val dev = device ?: return
        try {
            inst.getApplicationInfo(APP_ID, dev, object : ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    ScrapeHub.log("Watch app found")
                }
                override fun onApplicationNotInstalled(applicationId: String) {
                    ScrapeHub.log("Open Pokit Pro on the watch")
                }
            })
        } catch (_: Exception) {
        }
    }
}
