package com.fengbro.player.core.media

import com.fengbro.player.core.model.AudioInfo
import com.fengbro.player.core.model.VideoInfo
import java.io.File
import kotlin.math.abs

object MediaMetadata {
    val audioExtensions = listOf(
        ".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".wma", ".aiff", ".opus",
    )
    val videoExtensions = listOf(
        ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".webm", ".m4v", ".ts", ".flv", ".3gp",
    )
    val subtitleExtensions = listOf(".srt", ".ass", ".ssa", ".vtt", ".sub")

    val coverFileNames = listOf(
        "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
        "folder.jpg", "folder.jpeg", "folder.png",
        "album.jpg", "album.jpeg", "album.png",
        "AlbumArt.jpg", "AlbumArtSmall.jpg", "front.jpg", "Front.jpg",
    )

    fun isAudio(path: String): Boolean =
        audioExtensions.any { path.endsWith(it, ignoreCase = true) }

    fun isVideo(path: String): Boolean =
        videoExtensions.any { path.endsWith(it, ignoreCase = true) }

    fun isSubtitle(path: String): Boolean =
        subtitleExtensions.any { path.endsWith(it, ignoreCase = true) }

    fun isSupportedMedia(path: String): Boolean = isAudio(path) || isVideo(path)

    fun looksLikeStreamPlaylist(path: String): Boolean =
        path.endsWith(".m3u8", ignoreCase = true) ||
            path.endsWith(".m3u", ignoreCase = true) ||
            path.endsWith(".mpd", ignoreCase = true)

    fun findSidecarSubtitle(videoPath: String): String? {
        if (videoPath.isBlank()) return null
        val file = File(videoPath)
        val dir = file.parentFile ?: return null
        val stem = file.nameWithoutExtension
        if (stem.isBlank()) return null
        return subtitleExtensions
            .map { File(dir, stem + it) }
            .firstOrNull { it.isFile }
            ?.absolutePath
    }

    fun tryLoadSidecarCover(mediaPath: String): ByteArray? {
        val dir = File(mediaPath).parentFile ?: return null
        if (!dir.isDirectory) return null
        for (name in coverFileNames) {
            val candidate = File(dir, name)
            if (candidate.isFile && candidate.length() > 0) {
                return runCatching { candidate.readBytes() }.getOrNull()
            }
        }
        return null
    }

    fun fallbackAudio(path: String): AudioInfo {
        val ext = extensionLabel(path)
        val name = displayStem(path)
        return AudioInfo(name, "本機檔案", "—:—", ext, "—")
    }

    fun fallbackVideo(path: String): VideoInfo {
        val ext = extensionLabel(path)
        val name = displayStem(path)
        return VideoInfo(name, "本機影片", "—:—", ext)
    }

    fun formatDuration(lengthMs: Long): String {
        if (lengthMs <= 0) return "00:00"
        val totalSeconds = lengthMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours >= 1) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    fun hueFromPath(path: String): Int = abs(path.hashCode() % 360)

    fun extensionLabel(path: String): String {
        val ext = path.substringAfterLast('.', "").trim()
        return ext.uppercase().ifBlank { "MEDIA" }
    }

    fun displayStem(path: String): String {
        val cleaned = path.trim().trimEnd('/')
        val name = cleaned.substringAfterLast('/').substringAfterLast('\\')
        val stem = name.substringBeforeLast('.', name)
        return stem.ifBlank { name.ifBlank { "未命名" } }
    }
}
