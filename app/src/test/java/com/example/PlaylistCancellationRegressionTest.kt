package com.example

import com.example.ui.playlistMembershipOrFalse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class PlaylistCancellationRegressionTest {

    @Test
    fun cancellationDuringMembershipCheckNeverFallsThroughToPlaylistAddition() = runBlocking {
        val additionAttempted = AtomicBoolean(false)
        val cancellation = CancellationException("test cancellation")
        var observed: CancellationException? = null

        try {
            val alreadyInPlaylist = playlistMembershipOrFalse {
                throw cancellation
            }
            if (!alreadyInPlaylist) {
                additionAttempted.set(true)
            }
        } catch (caught: CancellationException) {
            observed = caught
        }

        assertTrue("The original cancellation must propagate", observed === cancellation)
        assertFalse("A cancelled membership check must not be treated as absent", additionAttempted.get())
    }

    @Test
    fun ordinaryMembershipLookupFailureUsesSafeFallback() = runBlocking {
        val alreadyInPlaylist = playlistMembershipOrFalse {
            throw IllegalStateException("temporary lookup failure")
        }
        assertFalse(alreadyInPlaylist)
    }
}
