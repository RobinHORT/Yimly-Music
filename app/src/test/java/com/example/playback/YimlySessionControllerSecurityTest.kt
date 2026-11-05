package com.example.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YimlySessionControllerSecurityTest {

    @Test
    fun untrustedUnverifiedControllerCannotControlPlayback() {
        assertFalse(
            isPlaybackControllerAuthorized(
                isTrusted = false,
                isPackageNameVerified = false,
                isVerifiedAutoController = false,
                isVerifiedAutomotiveController = false
            )
        )
    }

    @Test
    fun trustedControllerCanControlPlayback() {
        assertTrue(
            isPlaybackControllerAuthorized(
                isTrusted = true,
                isPackageNameVerified = false,
                isVerifiedAutoController = false,
                isVerifiedAutomotiveController = false
            )
        )
    }

    @Test
    fun verifiedCarControllerCanControlPlayback() {
        assertTrue(
            isPlaybackControllerAuthorized(
                isTrusted = false,
                isPackageNameVerified = true,
                isVerifiedAutoController = true,
                isVerifiedAutomotiveController = false
            )
        )
    }

    @Test
    fun unverifiedCarPackageCannotBypassControllerTrust() {
        assertFalse(
            isPlaybackControllerAuthorized(
                isTrusted = false,
                isPackageNameVerified = false,
                isVerifiedAutoController = true,
                isVerifiedAutomotiveController = true
            )
        )
    }
}
