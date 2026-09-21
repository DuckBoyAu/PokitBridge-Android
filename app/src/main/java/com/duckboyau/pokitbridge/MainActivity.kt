package com.duckboyau.pokitbridge

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private var service: BridgeService? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as BridgeService.LocalBinder).getService()
            service?.addLogListener { msg ->
                runOnUiThread {
                    logView.append(msg + "\n")
                    scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logView = findViewById(R.id.log)
        scroll = findViewById(R.id.scroll)
        WatchBridge.init(this)
        ScrapeHub.listener = { msg ->
            runOnUiThread {
                logView.append(msg + "\n")
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
        findViewById<Button>(R.id.start).setOnClickListener {
            if (ensurePerms()) {
                BridgeService.start(this)
                bindService(Intent(this, BridgeService::class.java), conn, Context.BIND_AUTO_CREATE)
                log("Starting bridge…")
            }
        }
        findViewById<Button>(R.id.stop).setOnClickListener {
            try { unbindService(conn) } catch (_: Exception) {}
            BridgeService.stop(this)
            log("Stopped BLE")
        }
        findViewById<Button>(R.id.scrape).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            log("Turn on Pokit Bridge in Accessibility, then open the official Pokit app.")
        }
        log("Screen scrape: Enable screen scrape → turn on Pokit Bridge → open official Pokit. Watch should follow the on-screen number.")
    }

    private fun ensurePerms(): Boolean {
        val needed = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
            log("Grant Nearby devices + notifications, then tap Start again")
            return false
        }
        return true
    }

    private fun log(msg: String) {
        logView.append(msg + "\n")
    }
}
