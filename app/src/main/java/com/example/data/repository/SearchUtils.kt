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
     * Calculates relevance score for a song against a normalized query and query tokens.
     * Priority:
     * 1. Exact song-title match (score 100)
     * 2. Song-title prefix match (score 80)
     * 3. Song-title partial match (score 60)
     * 4. Artist match (score 40)
     * 5. Album match (score 20)
     * 6. Genre match (score 10)
     */
    fun getSongRelevanceScore(song: Song, query: String): Int {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return 0

        val normTitle = normalize(song.title)
        val normArtist = normalize(song.artist)
        val normAlbum = normalize(song.album)
        val normGenre = normalize(song.genre)

        if (normTitle == normalizedQuery) return 100
        if (normTitle.startsWith(normalizedQuery)) return 80
        if (normTitle.contains(normalizedQuery)) return 60
        if (normArtist.contains(normalizedQuery)) return 40
        if (normAlbum.contains(normalizedQuery)) return 20
        if (normGenre.contains(normalizedQuery)) return 10

        // Multi-token fallback check
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        if (queryTokens.isNotEmpty()) {
            if (queryTokens.all { normTitle.contains(it) }) return 50
            if (queryTokens.all { normArtist.contains(it) }) return 35
            if (queryTokens.all { normAlbum.contains(it) }) return 18
            if (queryTokens.all { normGenre.contains(it) }) return 8
        }

        return 0
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
