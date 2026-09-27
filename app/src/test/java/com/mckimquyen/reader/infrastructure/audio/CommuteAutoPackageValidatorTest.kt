package com.mckimquyen.reader.infrastructure.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommuteAutoPackageValidatorTest {

    private val ownPackage = "com.mckimquyen.reader"

    @Test
    fun `own app package is always trusted`() {
        assertTrue(CommuteAutoPackageValidator.isTrusted(ownPackage, ownPackage))
    }

    @Test
    fun `android auto gearhead package is trusted`() {
        assertTrue(
            CommuteAutoPackageValidator.isTrusted(
                "com.google.android.projection.gearhead",
                ownPackage,
            )
        )
    }

    @Test
    fun `android automotive os host package is trusted`() {
        assertTrue(
            CommuteAutoPackageValidator.isTrusted(
                "com.google.android.apps.automotive.templates.host",
                ownPackage,
            )
        )
    }

    @Test
    fun `car media center package is trusted`() {
        assertTrue(CommuteAutoPackageValidator.isTrusted("com.android.car.media", ownPackage))
    }

    @Test
    fun `unrelated package is rejected`() {
        assertFalse(CommuteAutoPackageValidator.isTrusted("com.malicious.app", ownPackage))
    }

    @Test
    fun `null package is rejected rather than crashing`() {
        assertFalse(CommuteAutoPackageValidator.isTrusted(null, ownPackage))
    }

    @Test
    fun `blank package is rejected`() {
        assertFalse(CommuteAutoPackageValidator.isTrusted("   ", ownPackage))
    }

    @Test
    fun `case mismatch is not trusted`() {
        // Package names are case-sensitive on Android; a near-miss must not silently pass.
        assertFalse(
            CommuteAutoPackageValidator.isTrusted(
                "COM.GOOGLE.ANDROID.PROJECTION.GEARHEAD",
                ownPackage,
            )
        )
    }
}
