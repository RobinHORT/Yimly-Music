package com.example

import com.example.data.models.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentalPlaybackTest {

    @Test
    fun testYimlyServerAudioUrlPatternForMainAndInstrumental() {
        val songWithInst = Song(
            id = "song_101",
            title = "All of the Lights",
            artist = "Kanye West",
            album = "My Beautiful Dark Twisted Fantasy",
            hasInstrumental = true
        )

        // 1. OFF -> /api/songs/{songId}/audio?type=main
        val mainUrl = songWithInst.audioUrl
        assertTrue(mainUrl.contains("/api/songs/song_101/audio?type=main"))

        // 2. ON -> /api/songs/{songId}/audio?type=instrumental
        val instUrl = songWithInst.directInstrumentalAudioUrl
        assertNotNull(instUrl)
        assertTrue(instUrl!!.contains("/api/songs/song_101/audio?type=instrumental"))
    }

    @Test
    fun testYimlyServerSongWithoutInstrumentalDoesNotGenerateInstrumentalUrl() {
        val songWithoutInst = Song(
            id = "solo_202",
            title = "Someone Like You",
            artist = "Adele",
            album = "21",
            hasInstrumental = false
        )

        // OFF -> /api/songs/{songId}/audio?type=main
        val mainUrl = songWithoutInst.audioUrl
        assertTrue(mainUrl.contains("/api/songs/solo_202/audio?type=main"))

        // ON -> null (no instrumental available)
        val instUrl = songWithoutInst.directInstrumentalAudioUrl
        assertNull(instUrl)
    }

    @Test
    fun testYimlyServerExplicitAudioPathsRespected() {
        val songWithExplicitPaths = Song(
            id = "song_303",
            title = "Power",
            artist = "Kanye West",
            album = "MBDTF",
            mainAudioPath = "/api/songs/song_303/audio?type=main",
            instrumentalAudioPath = "/api/songs/song_303/audio?type=instrumental",
            hasInstrumental = true
        )

        assertTrue(songWithExplicitPaths.audioUrl.contains("type=main"))
        assertNotNull(songWithExplicitPaths.directInstrumentalAudioUrl)
        assertTrue(songWithExplicitPaths.directInstrumentalAudioUrl!!.contains("type=instrumental"))
    }
}
