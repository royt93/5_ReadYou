package com.mckimquyen.reader.domain.p2psync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAccessTest {
    @Test fun permissionNameMatchesTheAndroidConstant() {
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LanAccess.PERMISSION)
    }

    @Test fun notRequiredBeforeAndroid17() {
        assertFalse(LanAccess.required(26))
        assertFalse(LanAccess.required(33))
        assertFalse(LanAccess.required(36))
    }

    @Test fun requiredFromAndroid17() {
        assertTrue(LanAccess.required(37))
        assertTrue(LanAccess.required(38))
    }

    @Test fun thresholdIsExactlyApi37() {
        assertEquals(37, LanAccess.REQUIRED_FROM_SDK)
        assertFalse(LanAccess.required(LanAccess.REQUIRED_FROM_SDK - 1))
        assertTrue(LanAccess.required(LanAccess.REQUIRED_FROM_SDK))
    }
}
