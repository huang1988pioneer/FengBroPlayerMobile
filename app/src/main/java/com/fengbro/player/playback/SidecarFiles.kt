package com.fengbro.player.playback

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.fengbro.player.core.media.MediaMetadata
import java.io.File

object SidecarFiles {
    fun findSubtitle(context: Context, mediaUri: Uri, displayName: String?): Uri? {
        val stem = stemOf(context, mediaUri, displayName) ?: return null
        return find(context, mediaUri, MediaMetadata.sidecarNames(stem)) { raw ->
            MediaMetadata.findSidecarSubtitle(raw)
        }
    }

    fun findLyric(context: Context, mediaUri: Uri, displayName: String?): Uri? {
        val stem = stemOf(context, mediaUri, displayName) ?: return null
        return find(context, mediaUri, MediaMetadata.lyricNames(stem)) { raw ->
            com.fengbro.player.core.lyrics.LrcParser.findSidecar(raw)
        }
    }

    fun pairSubtitles(files: List<Pair<Uri, String>>): Map<String, Uri> =
        pairByStem(files) { MediaMetadata.isSubtitle(it) }

    fun pairLyrics(files: List<Pair<Uri, String>>): Map<String, Uri> =
        pairByStem(files) { MediaMetadata.isLyric(it) }

    private fun pairByStem(
        files: List<Pair<Uri, String>>,
        predicate: (String) -> Boolean,
    ): Map<String, Uri> {
        return files
            .filter { predicate(it.second) }
            .associate { MediaMetadata.displayStem(it.second).lowercase() to it.first }
    }

    private fun stemOf(context: Context, mediaUri: Uri, displayName: String?): String? {
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: LocalMetadataReader.displayName(context.contentResolver, mediaUri)
        return MediaMetadata.displayStem(name).takeIf { it.isNotBlank() }
    }

    private fun find(
        context: Context,
        mediaUri: Uri,
        names: List<String>,
        filesystem: (String) -> String?,
    ): Uri? {
        if (names.isEmpty()) return null
        findOnFilesystem(mediaUri, filesystem)?.let { return it }
        findDocumentSibling(context, mediaUri, names)?.let { return it }
        findInMediaStore(context, names)?.let { return it }
        return null
    }

    private fun findOnFilesystem(mediaUri: Uri, filesystem: (String) -> String?): Uri? {
        val raw = when (mediaUri.scheme) {
            "file" -> mediaUri.path
            else -> mediaUri.toString().takeIf { it.startsWith("/") }
        } ?: return null
        val found = filesystem(raw) ?: return null
        return Uri.fromFile(File(found))
    }

    private fun findDocumentSibling(context: Context, mediaUri: Uri, names: List<String>): Uri? {
        if (!DocumentsContract.isDocumentUri(context, mediaUri)) return null
        val docId = runCatching { DocumentsContract.getDocumentId(mediaUri) }.getOrNull() ?: return null
        val authority = mediaUri.authority ?: return null
        for (fileName in names) {
            val siblingId = siblingDocumentId(docId, fileName) ?: continue
            val candidates = buildList {
                add(DocumentsContract.buildDocumentUri(authority, siblingId))
                if (DocumentsContract.isTreeUri(mediaUri)) {
                    add(DocumentsContract.buildDocumentUriUsingTree(mediaUri, siblingId))
                }
            }
            for (candidate in candidates) {
                if (documentExists(context, candidate)) return candidate
            }
        }
        return null
    }

    private fun siblingDocumentId(docId: String, fileName: String): String? {
        if (docId.isBlank()) return null
        val slash = docId.lastIndexOf('/')
        if (slash >= 0) return docId.substring(0, slash + 1) + fileName
        val colon = docId.indexOf(':')
        if (colon >= 0 && colon < docId.lastIndex) {
            val after = docId.substring(colon + 1)
            val nested = after.lastIndexOf('/')
            return if (nested >= 0) {
                docId.substring(0, colon + 1) + after.substring(0, nested + 1) + fileName
            } else {
                docId.substring(0, colon + 1) + fileName
            }
        }
        return null
    }

    private fun documentExists(context: Context, uri: Uri): Boolean {
        val fromDoc = DocumentFile.fromSingleUri(context, uri)
        if (fromDoc != null && fromDoc.exists() && fromDoc.length() > 0) return true
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                it.moveToFirst()
            } == true
        }.getOrDefault(false)
    }

    private fun findInMediaStore(context: Context, names: List<String>): Uri? {
        if (names.isEmpty()) return null
        val collection = MediaStore.Files.getContentUri("external")
        val selection = names.joinToString(" OR ") { "${MediaStore.MediaColumns.DISPLAY_NAME}=?" }
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                selection,
                names.toTypedArray(),
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val rows = mutableListOf<Pair<Long, String>>()
                while (cursor.moveToNext()) {
                    rows += cursor.getLong(idIndex) to cursor.getString(nameIndex)
                }
                val preferred = names.firstNotNullOfOrNull { want ->
                    rows.firstOrNull { it.second.equals(want, ignoreCase = true) }?.first
                } ?: return@use null
                ContentUris.withAppendedId(collection, preferred)
            }
        }.getOrNull()
    }
}
