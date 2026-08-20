package com.fengbro.player.playback

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.fengbro.player.core.media.MediaMetadata
import com.fengbro.player.core.model.AudioInfo
import com.fengbro.player.core.model.VideoInfo

object LocalMetadataReader {
    fun displayName(resolver: ContentResolver, uri: Uri): String {
        val queriedName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return queriedName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "媒體"
    }

    fun readAudio(context: Context, uri: Uri, fallbackName: String): AudioInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: MediaMetadata.displayStem(fallbackName)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "本機檔案"
            val lengthMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val bits = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
            val bitrate = if (bits != null && bits > 0) "${bits / 1000}kbps" else "—"
            val cover = retriever.embeddedPicture?.takeIf { it.size > 256 }
            AudioInfo(
                title = title,
                artist = artist,
                duration = MediaMetadata.formatDuration(lengthMs),
                format = MediaMetadata.extensionLabel(fallbackName),
                bitrate = bitrate,
                lengthMs = lengthMs,
                coverArt = cover,
            )
        } catch (_: Exception) {
            MediaMetadata.fallbackAudio(fallbackName)
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun readVideo(context: Context, uri: Uri, fallbackName: String): VideoInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: MediaMetadata.displayStem(fallbackName)
            val lengthMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            VideoInfo(
                title = title,
                channel = "本機影片",
                duration = MediaMetadata.formatDuration(lengthMs),
                format = MediaMetadata.extensionLabel(fallbackName),
                lengthMs = lengthMs,
                width = width,
                height = height,
                videoCodec = mime.substringAfter('/', "").uppercase(),
            )
        } catch (_: Exception) {
            MediaMetadata.fallbackVideo(fallbackName)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
