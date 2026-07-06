package com.example.medgemma

data class ParsedStats(
    val summary: String,
    val details: String
)

private val STATS_REGEX = Regex(
    """(\d+)\s+tokens\s*•\s*([\d.]+)s\s*•\s*([\d.]+)\s*t/s(?:\s*\(native\s*([\d.]+)\s*t/s\))?"""
)

fun formatMessageStats(raw: String): ParsedStats {
    val match = STATS_REGEX.find(raw.trim())
    if (match == null) {
        return ParsedStats(summary = "Response complete", details = raw)
    }
    val (tokens, seconds, tps, nativeTps) = match.destructured
    val secondsFloat = seconds.toFloatOrNull() ?: 0f
    val summary = when {
        secondsFloat < 1f -> "Answered in under a second"
        secondsFloat < 60f -> "Answered in ${secondsFloat.toInt().coerceAtLeast(1)}s"
        else -> {
            val mins = (secondsFloat / 60).toInt()
            val secs = (secondsFloat % 60).toInt()
            "Answered in ${mins}m ${secs}s"
        }
    }
    val details = buildString {
        append("$tokens tokens · ${seconds}s · $tps t/s")
        if (nativeTps.isNotBlank()) append(" (native $nativeTps t/s)")
    }
    return ParsedStats(summary = summary, details = details)
}