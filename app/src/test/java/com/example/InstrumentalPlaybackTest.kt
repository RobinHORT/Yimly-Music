package com.example

import com.example.data.models.Song
import com.example.data.repository.MusicRepository
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

    @Test
    fun testMatchingInstrumentalWithBracketNaming() {
        val mainSong = Song(
            id = "main_1",
            title = "Song Title",
            artist = "Artist Name",
            album = "Album"
        )
        val instSong = Song(
            id = "inst_1",
            title = "Song Title [Instrumental]",
            artist = "Artist Name",
            album = "Album",
            isInstrumental = true
        )

        val matched = MusicRepository.findMatchingInstrumental(mainSong, listOf(mainSong, instSong))
        assertNotNull(matched)
        assertEquals("inst_1", matched?.id)
    }

    @Test
    fun testMatchingInstrumentalWithFilenameStem() {
        val mainSong = Song(
            id = "main_2",
            title = "Artist - Song Name",
            artist = "Artist",
            mainAudioPath = "/music/Artist - Song Name.mp3"
        )
        val instSong = Song(
            id = "inst_2",
            title = "Artist - Song Name [Instrumental]",
            artist = "Artist",
            mainAudioPath = "/music/Artist - Song Name [Instrumental].mp3",
            isInstrumental = true
        )

        val matched = MusicRepository.findMatchingInstrumental(mainSong, listOf(mainSong, instSong))
        assertNotNull(matched)
        assertEquals("inst_2", matched?.id)
    }

    @Test
    fun testMatchingInstrumentalWithArtistSeparators() {
        val mainSong = Song(
            id = "main_3",
            title = "Die With A Smile",
            artist = "Lady Gaga, Bruno Mars",
            album = "Die With A Smile - Single"
        )
        val instSong = Song(
            id = "inst_3",
            title = "Die With A Smile (Instrumental)",
            artist = "Bruno Mars; Lady Gaga",
            album = "Instrumentals",
            isInstrumental = true
        )

        val matched = MusicRepository.findMatchingInstrumental(mainSong, listOf(mainSong, instSong))
        assertNotNull(matched)
        assertEquals("inst_3", matched?.id)
    }

    @Test
    fun testMatchingNormalSongFromInstrumental() {
        val instSong = Song(
            id = "inst_4",
            title = "Blinding Lights (Inst)",
            artist = "The Weeknd",
            isInstrumental = true
        )
        val normalSong = Song(
            id = "norm_4",
            title = "Blinding Lights",
            artist = "The Weeknd"
        )

        val matched = MusicRepository.findMatchingNormalSong(instSong, listOf(instSong, normalSong))
        assertNotNull(matched)
        assertEquals("norm_4", matched?.id)
    }
}
