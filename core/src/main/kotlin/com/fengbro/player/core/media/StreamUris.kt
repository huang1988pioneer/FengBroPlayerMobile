package com.fengbro.player.core.media

import java.net.URI

object StreamUris {
    private val allowedSchemes = setOf(
        "http", "https", "rtsp", "rtsps", "rtmp", "rtmps", "mms", "mmsh", "rtp",
    )

    fun tryNormalize(url: String?): URI? {
        if (url.isNullOrBlank()) return null
        var s = url.trim().trim('<', '>', '"', '\'')
        s = when {
            s.startsWith("//") -> "https:$s"
            "://" !in s -> "https://$s"
            else -> s
        }
        val parsed = runCatching { URI(s) }.getOrNull() ?: return null
        if (!parsed.isAbsolute) return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        if (scheme !in allowedSchemes) return null
        return parsed
    }

    fun needsExtraction(url: String?): Boolean {
        val uri = tryNormalize(url) ?: return false
        return needsExtraction(uri)
    }

    fun needsExtraction(uri: URI): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return host.contains("youtube.com") ||
            host.contains("youtu.be") ||
            host.contains("youtube-nocookie.com") ||
            host.contains("music.youtube.com") ||
            host.contains("twitch.tv") ||
            host.contains("bilibili.com") ||
            host.contains("nicovideo.jp")
    }

    fun isPlaceholderNetworkTitle(title: String): Boolean {
        if (title.isBlank()) return true
        if (title == "YouTube 影片") return true
        return title.contains("youtube.com", ignoreCase = true) ||
            title.contains("youtu.be", ignoreCase = true)
    }

    fun titleFromUri(uri: URI): String {
        val path = uri.path.orEmpty()
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        val decoded = runCatching { java.net.URLDecoder.decode(name, Charsets.UTF_8) }.getOrDefault(name)
        return if (decoded.isNotBlank() && decoded != "/" && path != "/") decoded else uri.host.orEmpty()
    }
}
