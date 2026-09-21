package com.duckboyau.pokitbridge

data class ScrapedReading(
    val value: Float,
    val mode: Int,
    val status: Int,
    val label: String
)

object ReadingParser {
    fun parseLabel(raw: String, modeHint: String): ScrapedReading? {
        val s = raw.replace("−", "-").replace("–", "-").replace(",", ".").replace(" ", "")
        if (s.equals("OL", true) || s == "---") {
            return ScrapedReading(0f, guessMode(modeHint), 2, "OL")
        }
        val m = Regex("""^[+-]?(?:\d+\.\d+|\d+|\.\d+)$""").find(s) ?: return null
        val n = m.value.toFloatOrNull() ?: return null
        val label = if (m.value.startsWith(".")) "0${m.value}" else m.value
        return ScrapedReading(n, guessMode(modeHint), 0, label)
    }

    fun parse(texts: List<String>): ScrapedReading? {
        val blob = texts.joinToString(" ")
        return parseLabel(blob.replace(" ", ""), blob)
    }

    fun candidates(texts: List<String>): List<String> = texts

    private fun guessMode(lowerSrc: String): Int {
        val lower = lowerSrc.lowercase()
        val ac = Regex("""\bac\b""").containsMatchIn(lower)
        return when {
            lower.contains("resist") || lower.contains("ohm") -> 5
            lower.contains("current") || lower.contains("amp") -> if (ac) 4 else 3
            lower.contains("volt") || lower.contains("v dc") || lower.contains("v ac") -> if (ac) 2 else 1
            lower.contains("diode") -> 6
            lower.contains("cont") -> 7
            lower.contains("temp") -> 8
            lower.contains("cap") -> 9
            else -> 1
        }
    }
}
