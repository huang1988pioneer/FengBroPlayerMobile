package com.fengbro.player.core.lyrics

import com.fengbro.player.core.model.LrcLine
import java.io.File
import java.nio.charset.Charset
import kotlin.math.max
import kotlin.math.roundToLong

object LrcParser {
    private val timestamp = Regex("""\[(\d{1,3}):(\d{1,2}(?:\.\d{1,3})?)]""")
    private val offset = Regex("""^\[offset:([+-]?\d+)]$""", RegexOption.IGNORE_CASE)

    fun load(path: String): List<LrcLine> {
        if (path.isBlank()) return emptyList()
        val file = File(path)
        if (!file.isFile) return emptyList()
        return parseBytes(file.readBytes())
    }

    fun parseBytes(bytes: ByteArray): List<LrcLine> {
        if (bytes.isEmpty()) return emptyList()
        return parse(String(bytes, detectEncoding(bytes)))
    }

    fun parse(text: String): List<LrcLine> {
        val result = mutableListOf<LrcLine>()
        var offsetMs = 0L
        for (raw in text.lineSequence()) {
            val offsetMatch = offset.matchEntire(raw.trim())
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: offsetMs
                continue
            }
            val matches = timestamp.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val lineText = timestamp.replace(raw, "").trim()
            for (match in matches) {
                val minutes = match.groupValues[1].toIntOrNull() ?: continue
                val seconds = match.groupValues[2].toDoubleOrNull() ?: continue
                val timeMs = max(0L, ((minutes * 60 + seconds) * 1000).roundToLong() + offsetMs)
                result += LrcLine(timeMs = timeMs, text = lineText)
            }
        }
        return result.sortedBy { it.timeMs }
    }

    fun findSidecar(mediaPath: String): String? {
        val file = File(mediaPath)
        val directory = file.parentFile ?: return null
        val stem = file.nameWithoutExtension
        if (stem.isBlank()) return null
        val candidate = File(directory, "$stem.lrc")
        return if (candidate.isFile) candidate.absolutePath else null
    }

    fun currentLine(lines: List<LrcLine>, timeMs: Long): LrcLine? {
        if (lines.isEmpty()) return null
        var low = 0
        var high = lines.lastIndex
        var current = -1
        while (low <= high) {
            val mid = low + (high - low) / 2
            if (lines[mid].timeMs <= timeMs) {
                current = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (current >= 0) lines[current] else null
    }

    private fun detectEncoding(bytes: ByteArray): Charset {
        return when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
    }
}
