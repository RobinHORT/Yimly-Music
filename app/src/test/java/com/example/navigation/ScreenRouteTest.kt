package com.example.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenRouteTest {

    @Test
    fun mediaDetailRoutesEncodeReservedIdCharacters() {
        val id = "library/id?part#fragment"

        assertEquals("album/library%2Fid%3Fpart%23fragment", Screen.AlbumDetail.createRoute(id))
        assertEquals("artist/library%2Fid%3Fpart%23fragment", Screen.ArtistDetail.createRoute(id))
        assertEquals("playlist/library%2Fid%3Fpart%23fragment", Screen.PlaylistDetail.createRoute(id))
    }

    @Test
    fun mediaDetailRoutesKeepOrdinaryIdsReadable() {
        val id = "507f1f77bcf86cd799439011"

        assertEquals("album/$id", Screen.AlbumDetail.createRoute(id))
        assertEquals("artist/$id", Screen.ArtistDetail.createRoute(id))
        assertEquals("playlist/$id", Screen.PlaylistDetail.createRoute(id))
    }
}
