package com.example

import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import com.example.lyrics.LyricsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testDeriveArtistsFromSongs() {
        val songs = listOf(
            Song(id = "1", title = "Cruel Summer", artist = "Taylor Swift", album = "Lover", explicitArtworkUrl = "https://img.com/lover.jpg", genre = "Pop"),
            Song(id = "2", title = "Blank Space", artist = "Taylor Swift", album = "1989", genre = "Pop"),
            Song(id = "3", title = "Blinding Lights", artist = "The Weeknd", album = "After Hours", explicitArtworkUrl = "https://img.com/weeknd.jpg", genre = "R&B"),
            Song(id = "4", title = "Unknown Track", artist = "", album = "Unknown")
        )

        val artists = MusicRepository.deriveArtistsFromSongs(songs)

        assertEquals(2, artists.size)
        val taylor = artists.find { it.name == "Taylor Swift" }
        assertNotNull(taylor)
        assertEquals(2, taylor?.songCount)
        assertEquals("https://img.com/lover.jpg", taylor?.explicitAvatarUrl)
        assertTrue(taylor?.genres?.contains("Pop") == true)

        val weeknd = artists.find { it.name == "The Weeknd" }
        assertNotNull(weeknd)
        assertEquals(1, weeknd?.songCount)
        assertEquals("https://img.com/weeknd.jpg", weeknd?.explicitAvatarUrl)
    }

    @Test
    fun testMultipleArtistsParsingWithSemicolon() {
        val songs = listOf(
            Song(id = "1", title = "No Air", artist = "Chris Brown;Brooke Fraser", album = "Collaboration", genre = "Pop"),
            Song(id = "2", title = "Forever", artist = "Chris Brown", album = "Exclusive", genre = "R&B"),
            Song(id = "3", title = "Shadowfeet", artist = "Brooke Fraser", album = "Albertine", genre = "Acoustic")
        )

        val artists = MusicRepository.deriveArtistsFromSongs(songs)

        assertEquals(2, artists.size)
        val chris = artists.find { it.name.equals("Chris Brown", ignoreCase = true) }
        val brooke = artists.find { it.name.equals("Brooke Fraser", ignoreCase = true) }

        assertNotNull(chris)
        assertNotNull(brooke)
        assertEquals(2, chris?.songCount)
        assertEquals(2, brooke?.songCount)
    }

    @Test
    fun testInstrumentalMatchingWithMultipleArtists() {
        val mainSong = Song(
            id = "main_1",
            title = "Under the Influence",
            artist = "Chris Brown;Brooke Fraser",
            album = "Indigo",
            mainAudioPath = "/audio/main_1.mp3"
        )

        val candidateInstrumental = Song(
            id = "inst_1",
            title = "Under the Influence (Instrumental)",
            artist = "Brooke Fraser;Chris Brown",
            album = "Indigo",
            mainAudioPath = "/audio/inst_1.mp3",
            isInstrumental = true
        )

        val unrelatedSong = Song(
            id = "other_1",
            title = "Under the Influence",
            artist = "Different Artist",
            album = "Indigo",
            isInstrumental = true
        )

        val allSongs = listOf(mainSong, candidateInstrumental, unrelatedSong)

        val matched = MusicRepository.findMatchingInstrumental(mainSong, allSongs)
        assertNotNull(matched)
        assertEquals("inst_1", matched?.id)
    }

    @Test
    fun testInstrumentalExcludedFromLibraryArtistsAndAlbums() {
        val songs = listOf(
            Song(id = "1", title = "Song A", artist = "Artist One", album = "Album A"),
            Song(id = "2", title = "Song A (Instrumental)", artist = "Artist One", album = "Album A", isInstrumental = true)
        )

        val artists = MusicRepository.deriveArtistsFromSongs(songs)
        val albums = MusicRepository.deriveAlbumsFromSongs(songs)

        assertEquals(1, artists.size)
        assertEquals(1, artists.first().songCount)

        assertEquals(1, albums.size)
        assertEquals(1, albums.first().songCount)
    }

    @Test
    fun testNoMatchingInstrumentalReturnsNull() {
        val mainSong = Song(
            id = "solo_1",
            title = "Unique Track",
            artist = "Solo Artist",
            album = "Solo Album"
        )

        val otherSongs = listOf(
            Song(id = "other_1", title = "Different Song (Instrumental)", artist = "Solo Artist", isInstrumental = true)
        )

        val matched = MusicRepository.findMatchingInstrumental(mainSong, listOf(mainSong) + otherSongs)
        assertNull(matched)
    }

    @Test
    fun testDeriveAlbumsFromSongs() {
        val songs = listOf(
            Song(id = "1", title = "Cruel Summer", artist = "Taylor Swift", album = "Lover", year = 2019, explicitArtworkUrl = "https://img.com/lover.jpg", genre = "Pop"),
            Song(id = "2", title = "Lover", artist = "Taylor Swift", album = "Lover", year = 2019, explicitArtworkUrl = "https://img.com/lover.jpg", genre = "Pop"),
            Song(id = "3", title = "Blinding Lights", artist = "The Weeknd", album = "After Hours", year = 2020, genre = "R&B"),
            Song(id = "4", title = "Standalone Single", artist = "Dua Lipa", album = "", year = 2024)
        )

        val albums = MusicRepository.deriveAlbumsFromSongs(songs)

        assertEquals(3, albums.size)
        val loverAlbum = albums.find { it.title == "Lover" }
        assertNotNull(loverAlbum)
        assertEquals(2, loverAlbum?.songCount)
        assertEquals("Taylor Swift", loverAlbum?.artist)
        assertEquals(2019, loverAlbum?.year)
        assertEquals("https://img.com/lover.jpg", loverAlbum?.explicitArtworkUrl)

        val afterHoursAlbum = albums.find { it.title == "After Hours" }
        assertNotNull(afterHoursAlbum)
        assertEquals(1, afterHoursAlbum?.songCount)
        assertEquals("The Weeknd", afterHoursAlbum?.artist)
        assertEquals(2020, afterHoursAlbum?.year)

        val singleAlbum = albums.find { it.artist == "Dua Lipa" }
        assertNotNull(singleAlbum)
        assertEquals(1, singleAlbum?.songCount)
    }

    @Test
    fun testLyricsParserWithSyncedLrc() {
        val lrc = """
            [ti:Test Song]
            [ar:Test Artist]
            [offset:500]
            [00:10.00] First line of song
            [00:20.50] Second line of song
            [00:35.00] Third line of song
        """.trimIndent()

        val parsed = LyricsParser.parse(lrc, songId = "s1")
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)
        // 10.00s + 500ms offset = 10500ms
        assertEquals(10500L, parsed.lines[0].timeMs)
        assertEquals("First line of song", parsed.lines[0].text)
        // 20.50s + 500ms offset = 21000ms
        assertEquals(21000L, parsed.lines[1].timeMs)
        assertEquals("Second line of song", parsed.lines[1].text)
        // 35.00s + 500ms offset = 35500ms
        assertEquals(35500L, parsed.lines[2].timeMs)
        assertEquals("Third line of song", parsed.lines[2].text)
    }

    @Test
    fun testLyricsSlotStateCalculation() {
        val lrc = """
            [00:10.00] Line One
            [00:20.00] Line Two
            [00:30.00] Line Three
        """.trimIndent()

        val parsed = LyricsParser.parse(lrc, songId = "s2")

        // Before first line at 5000ms
        val beforeStart = LyricsParser.findActiveLyricSlots(parsed.lines, currentPositionMs = 5000L)
        assertEquals(-1, beforeStart.currentIndex)
        assertNull(beforeStart.currentLine)
        assertEquals("Line One", beforeStart.nextLine?.text)

        // During Line One at 12000ms
        val duringOne = LyricsParser.findActiveLyricSlots(parsed.lines, currentPositionMs = 12000L)
        assertEquals(0, duringOne.currentIndex)
        assertEquals("Line One", duringOne.currentLine?.text)
        assertNull(duringOne.previousLine)
        assertEquals("Line Two", duringOne.nextLine?.text)

        // During Line Two at 25000ms
        val duringTwo = LyricsParser.findActiveLyricSlots(parsed.lines, currentPositionMs = 25000L)
        assertEquals(1, duringTwo.currentIndex)
        assertEquals("Line Two", duringTwo.currentLine?.text)
        assertEquals("Line One", duringTwo.previousLine?.text)
        assertEquals("Line Three", duringTwo.nextLine?.text)
    }

    @Test
    fun testSongFormattedDuration() {
        val songZero = Song(id = "1", title = "A", artist = "B", album = "C", durationSeconds = 0L)
        assertEquals("0:00", songZero.formattedDuration())

        val songShort = Song(id = "2", title = "A", artist = "B", album = "C", durationSeconds = 65L)
        assertEquals("1:05", songShort.formattedDuration())

        val songLong = Song(id = "3", title = "A", artist = "B", album = "C", durationSeconds = 225L)
        assertEquals("3:45", songLong.formattedDuration())
    }

    @Test
    fun testTitleCleaningAndVariousInstrumentalSuffixes() {
        assertEquals("counting stars", MusicRepository.cleanSongTitle("Counting Stars"))
        assertEquals("counting stars", MusicRepository.cleanSongTitle("Counting Stars (Instrumental)"))
        assertEquals("counting stars", MusicRepository.cleanSongTitle("Counting Stars [Instrumental]"))
        assertEquals("counting stars", MusicRepository.cleanSongTitle("Counting Stars (Instrumental Version)"))
        assertEquals("counting stars", MusicRepository.cleanSongTitle("Counting Stars - Instrumental Version"))
        assertEquals("counting stars", MusicRepository.cleanSongTitle("Counting Stars - Instrumental"))
        assertEquals("counting stars", MusicRepository.cleanSongTitle("Counting Stars Instrumental"))
        assertEquals("counting stars", MusicRepository.cleanSongTitle("Counting Stars (Official Instrumental)"))
        assertEquals("counting stars", MusicRepository.cleanSongTitle("Counting Stars (Karaoke)"))
        assertEquals("counting stars", MusicRepository.cleanSongTitle("Counting Stars (Backing Track)"))
    }

    @Test
    fun testMultiArtistMatchingDifferentOrder() {
        val mainSong = Song(
            id = "s_main",
            title = "No Air",
            artist = "Chris Brown;Brooke Fraser",
            album = "Indigo",
            mainAudioPath = "/audio/no_air.mp3"
        )

        val instrumentalSong = Song(
            id = "s_inst",
            title = "No Air (Instrumental)",
            artist = "Brooke Fraser;Chris Brown",
            album = "Indigo",
            mainAudioPath = "/audio/no_air_inst.mp3",
            isInstrumental = true
        )

        val matched = MusicRepository.findMatchingInstrumental(mainSong, listOf(mainSong, instrumentalSong))
        assertNotNull(matched)
        assertEquals("s_inst", matched?.id)
    }

    @Test
    fun testUnrelatedInstrumentalSimilarTitleDoesNotMatch() {
        val mainSong = Song(
            id = "s1",
            title = "Stay",
            artist = "The Kid LAROI;Justin Bieber",
            album = "F*CK LOVE 3",
            mainAudioPath = "/audio/stay.mp3"
        )

        val unrelatedInst = Song(
            id = "s2",
            title = "Stay (Instrumental)",
            artist = "Rihanna;Mikky Ekko",
            album = "Unapologetic",
            mainAudioPath = "/audio/rihanna_stay_inst.mp3",
            isInstrumental = true
        )

        val matched = MusicRepository.findMatchingInstrumental(mainSong, listOf(mainSong, unrelatedInst))
        assertNull(matched)
    }

    @Test
    fun testNoMatchingInstrumentalReturnsNullSafely() {
        val mainSong = Song(
            id = "solo",
            title = "Alone Again",
            artist = "Solo Singer",
            album = "First Album",
            mainAudioPath = "/audio/alone.mp3"
        )

        val matched = MusicRepository.findMatchingInstrumental(mainSong, listOf(mainSong))
        assertNull(matched)
    }
}
