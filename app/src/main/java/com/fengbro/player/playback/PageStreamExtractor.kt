package com.fengbro.player.playback

import com.fengbro.player.core.media.StreamUris
import com.fengbro.player.core.model.ResolvedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PageStreamExtractor {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var ready = false

    fun initialize() {
        if (ready) return
        synchronized(this) {
            if (ready) return
            runCatching {
                NewPipe.init(OkHttpNewPipeDownloader(client), Localization("zh", "TW"))
                ready = true
            }
        }
    }

    suspend fun resolve(pageUrl: String): ResolvedStream? = withContext(Dispatchers.IO) {
        initialize()
        if (!ready) return@withContext null
        val uri = StreamUris.tryNormalize(pageUrl) ?: return@withContext null
        val absolute = expandShortPageUrl(uri.toString()) ?: return@withContext null
        val expandedHost = StreamUris.tryNormalize(absolute)?.host.orEmpty().lowercase()
        if (expandedHost == BILIBILI_HOST || expandedHost.endsWith(".$BILIBILI_HOST")) {
            return@withContext resolveBilibili(absolute)
        }
        runCatching {
            val info = StreamInfo.getInfo(absolute)
            val title = info.name?.takeIf { it.isNotBlank() } ?: StreamUris.tryNormalize(absolute)?.host.orEmpty()
            val uploader = info.uploaderName
            val duration = if (info.duration > 0) {
                com.fengbro.player.core.media.MediaMetadata.formatDuration(info.duration * 1000)
            } else {
                null
            }

            val progressive = info.videoStreams
                .filter { !it.content.isNullOrBlank() }
                .maxByOrNull { it.height }

            val videoOnly = info.videoOnlyStreams
                .filter { !it.content.isNullOrBlank() }
                .maxByOrNull { it.height }

            val audio = info.audioStreams
                .filter { !it.content.isNullOrBlank() }
                .maxByOrNull { it.averageBitrate }

            val primary = progressive?.content ?: videoOnly?.content ?: audio?.content
            if (primary.isNullOrBlank()) return@runCatching null

            ResolvedStream(
                pageUrl = absolute,
                title = title,
                primaryUrl = primary,
                audioUrl = if (progressive == null) audio?.content else null,
                isAudioOnly = progressive == null && videoOnly == null,
                duration = duration,
                uploader = uploader,
                referrer = absolute,
            )
        }.getOrNull()
    }

    private fun resolveBilibili(pageUrl: String): ResolvedStream? = runCatching {
        val bvid = BILIBILI_BVID_REGEX.find(pageUrl)?.groupValues?.getOrNull(1)
            ?: return@runCatching null
        val view = getBilibiliJson("https://api.bilibili.com/x/web-interface/view?bvid=$bvid")
            ?: return@runCatching null
        val data = view.optJSONObject("data") ?: return@runCatching null
        val cid = data.optLong("cid").takeIf { it > 0 } ?: return@runCatching null
        val title = data.optString("title").takeIf { it.isNotBlank() } ?: "Bilibili 影片"
        val uploader = data.optJSONObject("owner")?.optString("name")?.takeIf { it.isNotBlank() }
        val durationSeconds = data.optLong("duration")

        val play = getBilibiliJson(
            "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=16&fnval=0&platform=html5&high_quality=1",
            referrer = pageUrl,
        ) ?: return@runCatching null
        val playData = play.optJSONObject("data") ?: return@runCatching null
        val progressiveUrl = playData.optJSONArray("durl")
            ?.optJSONObject(0)
            ?.optString("url")
            ?.takeIf { it.isNotBlank() }
        val dash = playData.optJSONObject("dash")
        val videoUrl = highestBandwidthUrl(dash?.optJSONArray("video"))
        val audioUrl = highestBandwidthUrl(dash?.optJSONArray("audio"))
        val primaryUrl = progressiveUrl ?: videoUrl ?: audioUrl ?: return@runCatching null

        ResolvedStream(
            pageUrl = pageUrl,
            title = title,
            primaryUrl = primaryUrl,
            audioUrl = if (progressiveUrl == null && videoUrl != null) audioUrl else null,
            isAudioOnly = progressiveUrl == null && videoUrl == null,
            duration = durationSeconds.takeIf { it > 0 }?.let {
                com.fengbro.player.core.media.MediaMetadata.formatDuration(it * 1000)
            },
            uploader = uploader,
            referrer = pageUrl,
        )
    }.getOrNull()

    private fun getBilibiliJson(url: String, referrer: String? = null): JSONObject? {
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .apply { referrer?.let { header("Referer", it) } }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = response.body?.string()?.let(::JSONObject) ?: return@use null
            json.takeIf { it.optInt("code", -1) == 0 }
        }
    }

    private fun highestBandwidthUrl(streams: org.json.JSONArray?): String? {
        if (streams == null) return null
        return (0 until streams.length())
            .mapNotNull { index -> streams.optJSONObject(index) }
            .maxByOrNull { stream -> stream.optLong("bandwidth") }
            ?.let { stream ->
                stream.optString("baseUrl").takeIf { it.isNotBlank() }
                    ?: stream.optString("base_url").takeIf { it.isNotBlank() }
            }
    }

    private fun expandShortPageUrl(url: String): String? {
        val normalized = StreamUris.tryNormalize(url) ?: return null
        if (!normalized.host.equals(BILIBILI_SHORT_HOST, ignoreCase = true)) return normalized.toString()
        return runCatching {
            val request = okhttp3.Request.Builder()
                .url(normalized.toString())
                .head()
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url
                val host = finalUrl.host.lowercase()
                if (host != BILIBILI_HOST && !host.endsWith(".$BILIBILI_HOST")) return@use null
                finalUrl.newBuilder()
                    .host(BILIBILI_WEB_HOST)
                    .query(null)
                    .fragment(null)
                    .build()
                    .toString()
            }
        }.getOrNull()
    }

    fun serviceLabel(url: String): String {
        val host = StreamUris.tryNormalize(url)?.host.orEmpty()
        return when {
            host.contains("youtu") -> "YouTube"
            host.contains("bilibili") || host == "b23.tv" -> "Bilibili"
            host.contains("twitch") -> "Twitch"
            host.contains("niconico") || host.contains("nicovideo") -> "Niconico"
            else -> host
        }
    }

    private class OkHttpNewPipeDownloader(
        private val http: OkHttpClient,
    ) : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder().url(request.url())
            request.headers().forEach { (key, values) ->
                values.forEach { value -> builder.addHeader(key, value) }
            }
            when (request.httpMethod().uppercase()) {
                "POST" -> builder.post((request.dataToSend() ?: ByteArray(0)).toRequestBody(null))
                "HEAD" -> builder.head()
                else -> builder.get()
            }
            val originalRequest = builder.build()
            val httpRequest = if (originalRequest.url.host == YOUTUBE_API_HOST) {
                val webUrl = originalRequest.url.newBuilder().host(YOUTUBE_WEB_HOST).build()
                originalRequest.newBuilder().url(webUrl).build()
            } else {
                originalRequest
            }
            val response = http.newCall(httpRequest).execute()
            response.use {
                val body = response.body?.string()
                val headers = linkedMapOf<String, List<String>>()
                response.headers.toMultimap().forEach { (k, v) -> headers[k] = v }
                return Response(
                    response.code,
                    response.message,
                    headers,
                    body,
                    response.request.url.toString(),
                )
            }
        }
    }

    private const val YOUTUBE_API_HOST = "youtubei.googleapis.com"
    private const val YOUTUBE_WEB_HOST = "www.youtube.com"
    private const val BILIBILI_SHORT_HOST = "b23.tv"
    private const val BILIBILI_HOST = "bilibili.com"
    private const val BILIBILI_WEB_HOST = "www.bilibili.com"
    private val BILIBILI_BVID_REGEX = Regex("/video/(BV[0-9A-Za-z]+)", RegexOption.IGNORE_CASE)
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131 Mobile Safari/537.36"

}
