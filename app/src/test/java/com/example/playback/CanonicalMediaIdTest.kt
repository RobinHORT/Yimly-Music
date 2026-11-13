package com.example.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalMediaIdTest {

    @Test
    fun `parses normal canonical song IDs`() {
        val parsed = CanonicalMediaId.parse("song_123")
        assertEquals("song_123", parsed?.canonicalSongId)
        assertFalse(parsed!!.isInstrumental)
        assertEquals(MediaIdSource.DIRECT, parsed.source)
        assertNull(parsed.playlistId)
        assertFalse(parsed.isAndroidAutoSource)
        assertEquals("song_123", CanonicalMediaId.extractCanonicalSongId("song_123"))
        assertFalse(CanonicalMediaId.isAndroidAutoMediaId("song_123"))
    }

    @Test
    fun `parses instrumental suffixed song IDs`() {
        val parsed = CanonicalMediaId.parse("song_123_inst")
        assertEquals("song_123", parsed?.canonicalSongId)
        assertTrue(parsed!!.isInstrumental)
        assertEquals(MediaIdSource.DIRECT, parsed.source)
        assertNull(parsed.playlistId)
        assertFalse(parsed.isAndroidAutoSource)
    }

    @Test
    fun `parses all four Android Auto source prefixes`() {
        val allSong = CanonicalMediaId.parse("all_song|s1")
        assertEquals("s1", allSong?.canonicalSongId)
        assertEquals(MediaIdSource.ALL_SONGS, allSong?.source)
        assertFalse(allSong!!.isInstrumental)
        assertNull(allSong.playlistId)
        assertTrue(allSong.isAndroidAutoSource)

        val recentSong = CanonicalMediaId.parse("recent_song|s2")
        assertEquals("s2", recentSong?.canonicalSongId)
        assertEquals(MediaIdSource.RECENTLY_PLAYED, recentSong?.source)
        assertFalse(recentSong!!.isInstrumental)
        assertNull(recentSong.playlistId)
        assertTrue(recentSong.isAndroidAutoSource)

        val favoriteSong = CanonicalMediaId.parse("favorite_song|s3")
        assertEquals("s3", favoriteSong?.canonicalSongId)
        assertEquals(MediaIdSource.FAVORITES, favoriteSong?.source)
        assertFalse(favoriteSong!!.isInstrumental)
        assertNull(favoriteSong.playlistId)
        assertTrue(favoriteSong.isAndroidAutoSource)

        val playlistSong = CanonicalMediaId.parse("playlist_song|pl_42|s4")
        assertEquals("s4", playlistSong?.canonicalSongId)
        assertEquals(MediaIdSource.PLAYLIST, playlistSong?.source)
        assertEquals("pl_42", playlistSong?.playlistId)
        assertFalse(playlistSong!!.isInstrumental)
        assertTrue(playlistSong.isAndroidAutoSource)
    }

    @Test
    fun `parses prefixed Android Auto IDs combined with instrumental suffix`() {
        val allInst = CanonicalMediaId.parse("all_song|s1_inst")
        assertEquals("s1", allInst?.canonicalSongId)
        assertEquals(MediaIdSource.ALL_SONGS, allInst?.source)
        assertTrue(allInst!!.isInstrumental)

        val recentInst = CanonicalMediaId.parse("recent_song|s2_inst")
        assertEquals("s2", recentInst?.canonicalSongId)
        assertEquals(MediaIdSource.RECENTLY_PLAYED, recentInst?.source)
        assertTrue(recentInst!!.isInstrumental)

        val favInst = CanonicalMediaId.parse("favorite_song|s3_inst")
        assertEquals("s3", favInst?.canonicalSongId)
        assertEquals(MediaIdSource.FAVORITES, favInst?.source)
        assertTrue(favInst!!.isInstrumental)

        val playlistInst = CanonicalMediaId.parse("playlist_song|pl_inst|s4_inst")
        assertEquals("s4", playlistInst?.canonicalSongId)
        assertEquals("pl_inst", playlistInst?.playlistId)
        assertEquals(MediaIdSource.PLAYLIST, playlistInst?.source)
        assertTrue(playlistInst!!.isInstrumental)
    }

    @Test
    fun `rejects blank and malformed IDs safely without confusing playlist ID for song ID`() {
        val invalidIds = listOf(
            null,
            "",
            "   ",
            "_inst",
            "   _inst",
            "all_song|",
            "all_song|   ",
            "all_song|_inst",
            "recent_song|",
            "favorite_song|",
            "playlist_song|",
            "playlist_song|pl_only",
            "playlist_song|pl_only_inst",
            "playlist_song|pl_1|",
            "playlist_song||s1",
            "playlist_song|pl_1|_inst",
            "all_song|s1|extra",
            "playlist_song|pl_1|s1|extra"
        )

        for (invalid in invalidIds) {
            assertNull("Expected null for invalid mediaId='$invalid'", CanonicalMediaId.parse(invalid))
            assertNull("Expected null canonical ID for invalid mediaId='$invalid'", CanonicalMediaId.extractCanonicalSongId(invalid))
            assertFalse("Expected false for isAndroidAutoMediaId('$invalid')", CanonicalMediaId.isAndroidAutoMediaId(invalid))
        }
    }

    @Test
    fun `rejects unknown prefixes safely`() {
        val unknownIds = listOf(
            "unknown|s1",
            "playlist|pl_1",
            "album_song|alb_1|s1",
            "|s1"
        )

        for (unknown in unknownIds) {
            assertNull("Expected null for unknown prefix mediaId='$unknown'", CanonicalMediaId.parse(unknown))
            assertNull(CanonicalMediaId.extractCanonicalSongId(unknown))
            assertFalse(CanonicalMediaId.isAndroidAutoMediaId(unknown))
        }
    }

    @Test
    fun `builder helpers produce expected wire IDs`() {
        assertEquals("s1", CanonicalMediaId.forPlayback("s1", isInstrumental = false))
        assertEquals("s1_inst", CanonicalMediaId.forPlayback("s1", isInstrumental = true))
        assertEquals("all_song|s1", CanonicalMediaId.forAllSongs("s1"))
        assertEquals("recent_song|s1", CanonicalMediaId.forRecentlyPlayed("s1"))
        assertEquals("favorite_song|s1", CanonicalMediaId.forFavorites("s1"))
        assertEquals("playlist_song|pl_1|s100", CanonicalMediaId.forPlaylist("pl_1", "s100"))
    }
}
