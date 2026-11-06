package com.example.playback

import com.example.playback.isPlaybackControllerAuthorized
import com.example.playback.isControllerPackageVerified
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YimlySessionSecurityRegressionTest {

    @Test
    fun testPlaybackControllerAuthorizationPolicy() {
        // Trusted controllers are always allowed
        assertTrue(isPlaybackControllerAuthorized(isTrusted = true, isPackageNameVerified = false, isVerifiedAutoController = false, isVerifiedAutomotiveController = false))
        
        // Verified package + verified auto = allowed
        assertTrue(isPlaybackControllerAuthorized(isTrusted = false, isPackageNameVerified = true, isVerifiedAutoController = true, isVerifiedAutomotiveController = false))
        
        // Verified package + verified automotive = allowed
        assertTrue(isPlaybackControllerAuthorized(isTrusted = false, isPackageNameVerified = true, isVerifiedAutoController = false, isVerifiedAutomotiveController = true))
        
        // Verified package but NO auto/automotive controller = DENIED
        assertFalse(isPlaybackControllerAuthorized(isTrusted = false, isPackageNameVerified = true, isVerifiedAutoController = false, isVerifiedAutomotiveController = false))
        
        // NO verified package = DENIED
        assertFalse(isPlaybackControllerAuthorized(isTrusted = false, isPackageNameVerified = false, isVerifiedAutoController = true, isVerifiedAutomotiveController = false))
    }

    @Test
    fun controllerPackageMustBelongToReportedUid() {
        assertTrue(isControllerPackageVerified("com.example.car", arrayOf("com.example.car", "com.example.shared")))
        assertFalse(isControllerPackageVerified("com.attacker.spoof", arrayOf("com.example.car")))
        assertFalse(isControllerPackageVerified("com.example.car", null))
        assertFalse(isControllerPackageVerified("com.example.car", emptyArray()))
        assertFalse(isControllerPackageVerified(null, arrayOf("com.example.car")))
        assertFalse(isControllerPackageVerified("", arrayOf("com.example.car")))
    }
}