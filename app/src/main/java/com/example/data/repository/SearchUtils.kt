package com.example.data.repository

import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Playlist
import com.example.data.models.Song
import java.text.Normalizer
import java.util.Locale

object SearchUtils {

    /**
     * Normalizes a string by:
     * 1. Converting to lowercase (Unicode aware)
     * 2. Removing diacritics / accents (e.g. é -> e, Beyoncé -> Beyonce)
     * 3. Replacing apostrophes, hyphens, punctuation with spaces or stripped equivalents
     * 4. Preserving Unicode letters (\\p{L}) and numbers (\\p{N})
     * 5. Collapsing multiple spaces
     */
    fun normalize(input: String?): String {
        if (input == null) return ""
        val nfd = Normalizer.normalize(input, Normalizer.Form.NFD)
        val withoutDiacritics = nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return withoutDiacritics
            .lowercase(Locale.ROOT)
            .replace("'", "")
            .replace("’", "")
            .replace("`", "")
            .replace("-", " ")
            .replace("_", " ")
            .replace("&", " and ")
            .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Matches a song against a user query supporting:
     * - Multi-word queries (e.g. "Ariana hate")
     * - Apostrophes ("dont stop" matches "Don't Stop")
     * - Diacritics ("Beyonce" matches "Beyoncé")
     * - Hyphens ("Jay Z" matches "Jay-Z")
     * - Non-Latin scripts (Japanese, Chinese, Cyrillic)
     */
    fun matchesSong(song: Song, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return false // Return false so punctuation/symbols only don't match everything

        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return false

        val combinedTarget = normalize("${song.title} ${song.artist} ${song.album} ${song.genre.orEmpty()}")

        return queryTokens.all { token ->
            combinedTarget.contains(token)
        }
    }

    /**
     * Scores song matches consistently for both phrase and multi-token queries.
     * A complete title-token match outranks matches found only in artist, album,
     * or genre metadata. Phrase matches receive the strongest scores.
     */
    fun getSongRelevanceScore(song: Song, query: String): Int {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return 0

        val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 0

        val title = normalize(song.title)
        val artist = normalize(song.artist)
        val album = normalize(song.album)
        val genre = normalize(song.genre)

        if (title == normalizedQuery) return 100
        if (title.startsWith(normalizedQuery)) return 90
        if (title.contains(normalizedQuery)) return 80

        if (tokens.all { title.contains(it) }) return 70
        if (tokens.all { artist.contains(it) }) return 40
        if (tokens.all { album.contains(it) }) return 20
        if (tokens.all { genre.contains(it) }) return 10

        // Search matching permits query terms to span song metadata fields.
        val combined = normalize("$title $artist $album $genre")
        if (tokens.all { combined.contains(it) }) return 5

        return 0
    }

    /**
     * The shared production ordering used by MusicRepository and unit tests.
     */
    fun sortSongsByRelevance(songs: List<Song>, query: String): List<Song> {
        return songs.sortedWith(
            compareByDescending<Song> { getSongRelevanceScore(it, query) }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
    }

    fun matchesArtist(artist: Artist, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return false
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return false
        val target = normalize("${artist.name} ${artist.genres.joinToString(" ")} ${artist.bio.orEmpty()}")
        return queryTokens.all { token -> target.contains(token) }
    }

    fun matchesAlbum(album: Album, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return false
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return false
        val target = normalize("${album.title} ${album.artist} ${album.genre.orEmpty()}")
        return queryTokens.all { token -> target.contains(token) }
    }

    fun matchesPlaylist(playlist: Playlist, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return false
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return false
        val target = normalize("${playlist.name} ${playlist.description.orEmpty()}")
        return queryTokens.all { token -> target.contains(token) }
    }
}
