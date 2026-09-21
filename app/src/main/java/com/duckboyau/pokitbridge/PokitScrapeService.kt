package com.duckboyau.pokitbridge

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

class PokitScrapeService : AccessibilityService() {

    private var lastKey = ""
    private var lastLogAt = 0L

    override fun onServiceConnected() {
        WatchBridge.init(this)
        ScrapeHub.log("Scrape on — large center readout only.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: event?.packageName?.toString()
        if (pkg != POKIT_PKG) return

        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val band = Rect(
            (w * 0.08f).toInt(),
            (h * 0.20f).toInt(),
            (w * 0.80f).toInt(),
            (h * 0.70f).toInt()
        )

        val pieces = ArrayList<Piece>()
        val modeBits = ArrayList<String>()
        walk(root, band, pieces, modeBits)

        val label = assembleCenter(pieces, band) ?: return
        val reading = ReadingParser.parseLabel(label, modeBits.joinToString(" ")) ?: return

        WatchBridge.post(reading.value, reading.mode, reading.status, reading.label)
        val key = "${reading.label}|${reading.mode}"
        if (key == lastKey) return
        lastKey = key
        ScrapeHub.log("Center ← ${reading.label}")
    }

    override fun onInterrupt() {}

    private fun walk(
        node: AccessibilityNodeInfo?,
        band: Rect,
        pieces: MutableList<Piece>,
        modeBits: MutableList<String>
    ) {
        if (node == null) return
        val t = node.text?.toString()?.trim().orEmpty()
        val d = node.contentDescription?.toString()?.trim().orEmpty()
        val text = t.ifEmpty { d }
        if (text.isNotEmpty()) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (looksLikeMode(text)) {
                modeBits.add(text)
            }
            if (r.centerX() in band.left..band.right && r.centerY() in band.top..band.bottom) {
                if (isReadoutToken(text)) {
                    pieces.add(Piece(r.left, r.top, r.width(), r.height(), text))
                }
            }
        }
        for (i in 0 until node.childCount) {
            walk(node.getChild(i), band, pieces, modeBits)
        }
    }

    private fun assembleCenter(pieces: List<Piece>, band: Rect): String? {
        if (pieces.isEmpty()) return null
        val complete = pieces.filter { COMPLETE.matches(normalize(it.t)) }
        if (complete.isNotEmpty()) {
            val cx = band.centerX()
            val cy = band.centerY()
            val best = complete.maxWithOrNull(
                compareBy<Piece> { it.w * it.h }
                    .thenByDescending { it.t.count { ch -> ch == '.' || ch == ',' } }
                    .thenBy { abs(it.x + it.w / 2 - cx) + abs(it.y + it.h / 2 - cy) }
            ) ?: return null
            return normalize(best.t)
        }
        val maxH = pieces.maxOf { it.h }
        val glyphs = pieces.filter {
            val t = it.t
            t == "." || t == "," || t == "·" || t == "-" || t == "−" || t == "–" ||
                it.h >= (maxH * 0.45f).toInt()
        }.sortedBy { it.x }
        val joined = normalize(glyphs.joinToString("") { it.t })
        return joined.ifEmpty { null }
    }

    private fun isReadoutToken(t: String): Boolean {
        if (t == "." || t == "," || t == "·" || t == "-" || t == "−" || t == "–") return true
        return t.any { it.isDigit() } && TOKEN.matches(t)
    }

    private fun looksLikeMode(t: String): Boolean {
        val s = t.lowercase()
        return s.contains("volt") || s.contains("amp") || s.contains("ohm") ||
            s.contains("dc") || s.contains("ac") ||
            s.contains("resist") || s.contains("diode") || s.contains("cont") ||
            s.contains("temp") || s.contains("cap")
    }

    private fun normalize(t: String) =
        t.replace("−", "-").replace("–", "-").replace(",", ".").replace(" ", "")

    private data class Piece(val x: Int, val y: Int, val w: Int, val h: Int, val t: String)

    companion object {
        const val POKIT_PKG = "com.ingenuity.pokit.dev"
        private val TOKEN = Regex("""^[+\-−–]?\d*[.,]?\d*$""")
        private val COMPLETE = Regex("""^[+-]?(?:\d+\.\d+|\d+|\.\d+)$""")
    }
}

object ScrapeHub {
    @Volatile var listener: ((String) -> Unit)? = null
    fun log(msg: String) {
        listener?.invoke(msg)
    }
}
