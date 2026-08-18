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
        val absolute = uri.toString()
        runCatching {
            val info = StreamInfo.getInfo(absolute)
            val title = info.name?.takeIf { it.isNotBlank() } ?: uri.host.orEmpty()
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

    fun serviceLabel(url: String): String {
        val host = StreamUris.tryNormalize(url)?.host.orEmpty()
        return when {
            host.contains("youtu") -> "YouTube"
            host.contains("bilibili") -> "Bilibili"
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
            http.newCall(builder.build()).execute().use { response ->
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

}
