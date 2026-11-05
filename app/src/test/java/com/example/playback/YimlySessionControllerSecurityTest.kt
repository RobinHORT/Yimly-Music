package com.example.playback

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YimlySessionControllerSecurityTest {

    private fun controller(
        trusted: Boolean,
        packageVerified: Boolean
    ) = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
        "com.example.controller",
        123,
        12345,
        1,
        1,
        trusted,
        Bundle.EMPTY,
        packageVerified
    )

    @Test
    fun untrustedUnverifiedControllerCannotControlPlayback() {
        assertFalse(
            isPlaybackControllerAuthorized(
                controller(trusted = false, packageVerified = false),
                isVerifiedAutoController = false,
                isVerifiedAutomotiveController = false
            )
        )
    }

    @Test
    fun trustedControllerCanControlPlayback() {
        assertTrue(
            isPlaybackControllerAuthorized(
                controller(trusted = true, packageVerified = false),
                isVerifiedAutoController = false,
                isVerifiedAutomotiveController = false
            )
        )
    }

    @Test
    fun verifiedCarControllerCanControlPlayback() {
        assertTrue(
            isPlaybackControllerAuthorized(
                controller(trusted = false, packageVerified = true),
                isVerifiedAutoController = true,
                isVerifiedAutomotiveController = false
            )
        )
    }

    @Test
    fun unverifiedCarPackageCannotBypassControllerTrust() {
        assertFalse(
            isPlaybackControllerAuthorized(
                controller(trusted = false, packageVerified = false),
                isVerifiedAutoController = true,
                isVerifiedAutomotiveController = true
            )
        )
    }
}
