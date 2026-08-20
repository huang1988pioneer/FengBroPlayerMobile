package com.fengbro.player.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fengbro.player.core.model.MediaItem
import com.fengbro.player.core.model.MediaKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaLibraryRepositoryTest {
    private lateinit var database: FengBroDatabase
    private lateinit var repository: MediaLibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FengBroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MediaLibraryRepository(database.mediaLibraryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordsHistoryAndSeparatesNetworkStreams() = runBlocking {
        repository.record(localItem())
        repository.record(networkItem())

        val recents = repository.recents.first { it.size == 2 }
        val streams = repository.streams.first { it.size == 1 }

        assertEquals("Network", recents.first().title)
        assertEquals("https://example.com/live.m3u8", streams.single().sourceUrl)
    }

    @Test
    fun replacesAndRestoresPlaylistInOrder() = runBlocking {
        val expected = listOf(networkItem(), localItem())

        repository.replacePlaylist(expected)
        val restored = repository.loadPlaylist()

        assertEquals(expected.map { it.id }, restored.map { it.id })
        assertEquals(listOf(1, 2), restored.map { it.index })
        assertTrue(restored.first().isNetworkSource)
    }

    private fun localItem() = MediaItem(
        id = "local",
        index = 1,
        title = "Local",
        kind = MediaKind.Audio,
        filePath = "content://media/local",
    )

    private fun networkItem() = MediaItem(
        id = "network",
        index = 2,
        title = "Network",
        kind = MediaKind.Video,
        sourceUrl = "https://example.com/live.m3u8",
    )
}
