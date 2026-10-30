package com.example

import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Playlist
import com.example.data.models.Song
import com.example.data.repository.SearchUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchEngineComprehensiveTest {

    @Test
    fun testUnicodeJapaneseQuery() {
        val song = Song(id = "1", title = "夜に駆ける", artist = "YOASOBI", album = "The Book", durationSeconds = 250L)
        assertTrue(SearchUtils.matchesSong(song, "夜に"))
        assertTrue(SearchUtils.matchesSong(song, "夜に駆ける"))
        assertTrue(SearchUtils.matchesSong(song, "YOASOBI"))
        assertFalse(SearchUtils.matchesSong(song, "怪物"))
    }

    @Test
    fun testUnicodeChineseQuery() {
        val song = Song(id = "2", title = "青花瓷", artist = "周杰伦", album = "Jay Chou", durationSeconds = 240L)
        assertTrue(SearchUtils.matchesSong(song, "青花"))
        assertTrue(SearchUtils.matchesSong(song, "周杰伦"))
        assertFalse(SearchUtils.matchesSong(song, "稻香"))
    }

    @Test
    fun testUnicodeCyrillicQuery() {
        val song = Song(id = "3", title = "Кукушка", artist = "Кино", album = "Черный альбом", durationSeconds = 300L)
        assertTrue(SearchUtils.matchesSong(song, "Кукуш"))
        assertTrue(SearchUtils.matchesSong(song, "Кино"))
        assertFalse(SearchUtils.matchesSong(song, "Группа"))
    }

    @Test
    fun testLatinAccentedQuery() {
        val song = Song(id = "4", title = "Café", artist = "Beyoncé", album = "Lemonade", durationSeconds = 200L)
        assertTrue(SearchUtils.matchesSong(song, "cafe"))
        assertTrue(SearchUtils.matchesSong(song, "beyonce"))
    }

    @Test
    fun testMixedLatinAndNonLatin() {
        val song = Song(id = "5", title = "Tokyo Night 東京", artist = "Synth Wave", album = "Retro", durationSeconds = 220L)
        assertTrue(SearchUtils.matchesSong(song, "tokyo"))
        assertTrue(SearchUtils.matchesSong(song, "東京"))
        assertTrue(SearchUtils.matchesSong(song, "tokyo 東京"))
    }

    @Test
    fun testInvalidAndEmptyQueries() {
        val song = Song(id = "6", title = "Normal Song", artist = "Artist", album = "Album", durationSeconds = 180L)
        // Empty query
        assertFalse(SearchUtils.matchesSong(song, ""))
        // Whitespace-only query
        assertFalse(SearchUtils.matchesSong(song, "   "))
        // Punctuation-only query must normalize to empty and return false, NOT matching the song
        assertFalse(SearchUtils.matchesSong(song, "---"))
        assertFalse(SearchUtils.matchesSong(song, "...?!"))
        // Symbols-only query
        assertFalse(SearchUtils.matchesSong(song, "@#$%^&*"))
    }

    @Test
    fun testMatchingVariations() {
        val song = Song(id = "7", title = "Don't Stop Me Now", artist = "Queen", album = "Jazz", genre = "Rock", durationSeconds = 210L)
        // Exact title match
        assertTrue(SearchUtils.matchesSong(song, "Don't Stop Me Now"))
        // Title prefix match
        assertTrue(SearchUtils.matchesSong(song, "Don't Stop"))
        // Partial title match
        assertTrue(SearchUtils.matchesSong(song, "Stop Me"))
        // Artist match
        assertTrue(SearchUtils.matchesSong(song, "Queen"))
        // Album match
        assertTrue(SearchUtils.matchesSong(song, "Jazz"))
        // Genre match
        assertTrue(SearchUtils.matchesSong(song, "Rock"))
        // Multi-word query
        assertTrue(SearchUtils.matchesSong(song, "Queen Rock"))
        // Case-insensitive query
        assertTrue(SearchUtils.matchesSong(song, "dont stop me now"))
        // Apostrophe & punctuation handling
        assertTrue(SearchUtils.matchesSong(song, "dont stop"))
    }

    @Test
    fun testRelevanceRankingOrder() {
        val songExact = Song(id = "s1", title = "Hero", artist = "Other", album = "Other", durationSeconds = 200L)
        val songPrefix = Song(id = "s2", title = "Heroic Anthem", artist = "Other", album = "Other", durationSeconds = 200L)
        val songPartial = Song(id = "s3", title = "My Hero", artist = "Other", album = "Other", durationSeconds = 200L)
        val songArtist = Song(id = "s4", title = "Ballad", artist = "Heroic Band", album = "Other", durationSeconds = 200L)
        val songAlbum = Song(id = "s5", title = "Song", artist = "Artist", album = "Hero Album", durationSeconds = 200L)
        val songGenre = Song(id = "s6", title = "Tune", artist = "Artist", album = "Album", genre = "Heroic", durationSeconds = 200L)

        val query = "Hero"

        val scoreExact = SearchUtils.getSongRelevanceScore(songExact, query)
        val scorePrefix = SearchUtils.getSongRelevanceScore(songPrefix, query)
        val scorePartial = SearchUtils.getSongRelevanceScore(songPartial, query)
        val scoreArtist = SearchUtils.getSongRelevanceScore(songArtist, query)
        val scoreAlbum = SearchUtils.getSongRelevanceScore(songAlbum, query)
        val scoreGenre = SearchUtils.getSongRelevanceScore(songGenre, query)

        assertTrue("Exact match score ($scoreExact) must be > prefix score ($scorePrefix)", scoreExact > scorePrefix)
        assertTrue("Prefix match score ($scorePrefix) must be > partial score ($scorePartial)", scorePrefix > scorePartial)
        assertTrue("Title match score ($scorePartial) must be > artist score ($scoreArtist)", scorePartial > scoreArtist)
        assertTrue("Artist match score ($scoreArtist) must be > album score ($scoreAlbum)", scoreArtist > scoreAlbum)
        assertTrue("Album match score ($scoreAlbum) must be > genre score ($scoreGenre)", scoreAlbum > scoreGenre)
    }

    @Test
    fun testProductionOrderingPrioritizesAllTitleTokensOverMetadataMatches() {
        val titleMatch = Song(
            id = "title",
            title = "Don't Stop",
            artist = "Queen",
            album = "Jazz",
            durationSeconds = 200L
        )
        val artistOnly = Song(
            id = "artist",
            title = "Other Song",
            artist = "Don't Stop Band",
            album = "Album",
            durationSeconds = 200L
        )

        val ordered = SearchUtils.sortSongsByRelevance(
            listOf(artistOnly, titleMatch),
            "dont stop"
        )

        assertEquals("title", ordered.first().id)
        assertTrue(
            SearchUtils.getSongRelevanceScore(titleMatch, "dont stop") >
                SearchUtils.getSongRelevanceScore(artistOnly, "dont stop")
        )
    }

    @Test
    fun testProductionOrderingUsesStableTitleAndIdTieBreakers() {
        val sA = Song(id = "sa", title = "Alpha Song", artist = "Band", album = "Album", durationSeconds = 200L)
        val sB = Song(id = "sb", title = "Alpha Tune", artist = "Band", album = "Album", durationSeconds = 200L)
        val sameTitleZ = Song(id = "z", title = "Alpha Song", artist = "Band", album = "Album", durationSeconds = 200L)

        val ordered = SearchUtils.sortSongsByRelevance(
            listOf(sB, sameTitleZ, sA),
            "Alpha"
        )

        assertEquals(listOf("sa", "z", "sb"), ordered.map { it.id })
    }
}
