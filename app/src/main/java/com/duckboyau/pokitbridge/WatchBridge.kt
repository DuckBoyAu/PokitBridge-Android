package com.duckboyau.pokitbridge

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object WatchBridge {
    fun init(ctx: Context) {
        GarminLink.init(ctx.applicationContext)
    }

    @Volatile private var lastHttpAt = 0L

    fun post(value: Float, mode: Int, status: Int, display: String? = null) {
        GarminLink.send(value, mode, status, display)
        val now = System.currentTimeMillis()
        if (now - lastHttpAt < 5000) return
        lastHttpAt = now
        thread(name = "watch-post") {
            try {
                val conn = URL(PokitUuids.WATCH_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                val body = JSONObject()
                    .put("v", value.toDouble())
                    .put("m", mode)
                    .put("s", status)
                    .put("d", display ?: "")
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.inputStream.close()
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
