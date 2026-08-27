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
     * 1. Converting to lowercase
     * 2. Removing diacritics / accents (e.g. é -> e, Beyoncé -> Beyonce)
     * 3. Replacing apostrophes, hyphens, punctuation with spaces or stripped equivalents
     * 4. Collapsing multiple spaces
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
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Matches a song against a user query supporting:
     * - Multi-word queries (e.g. "Ariana hate")
     * - Apostrophes ("dont stop" matches "Don't Stop")
     * - Diacritics ("Beyonce" matches "Beyoncé")
     * - Hyphens ("Jay Z" matches "Jay-Z")
     * - Collaborations / multiple artists
     */
    fun matchesSong(song: Song, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return true

        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return true

        val combinedTarget = normalize("${song.title} ${song.artist} ${song.album} ${song.genre.orEmpty()}")

        // All tokens from query must exist in combined target text
        return queryTokens.all { token ->
            combinedTarget.contains(token)
        }
    }

    fun matchesArtist(artist: Artist, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return true
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        val target = normalize("${artist.name} ${artist.genres.joinToString(" ")} ${artist.bio.orEmpty()}")
        return queryTokens.all { token -> target.contains(token) }
    }

    fun matchesAlbum(album: Album, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return true
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        val target = normalize("${album.title} ${album.artist} ${album.genre.orEmpty()}")
        return queryTokens.all { token -> target.contains(token) }
    }

    fun matchesPlaylist(playlist: Playlist, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return true
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        val target = normalize("${playlist.name} ${playlist.description.orEmpty()}")
        return queryTokens.all { token -> target.contains(token) }
    }
}
