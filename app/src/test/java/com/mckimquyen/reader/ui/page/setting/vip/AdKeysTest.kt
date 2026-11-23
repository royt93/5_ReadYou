package com.mckimquyen.reader.ui.page.setting.vip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdKeysTest {

    @Test
    fun `redeem codes parse code and days, drop malformed entries`() {
        val parsed = AdKeys.parseRedeemCodes("ABC123:30, XYZ789:3 ,bad,NODAYS:,:5,ZERO:0,HUGE:366,TWO:1:2,OK:365")
        assertEquals(mapOf("ABC123" to 30, "XYZ789" to 3, "OK" to 365), parsed)
    }

    @Test
    fun `blank redeem codes give an empty map`() {
        assertTrue(AdKeys.parseRedeemCodes("").isEmpty())
        assertTrue(AdKeys.parseRedeemCodes("   ").isEmpty())
    }

    @Test
    fun `a later duplicate redeem code wins like the SDK map`() {
        assertEquals(mapOf("A" to 7), AdKeys.parseRedeemCodes("A:3,A:7"))
    }

    @Test
    fun `test device ids keep only 32 hex hashes, upper-cased and distinct`() {
        val hash = "0123456789ABCDEF0123456789ABCDEF"
        val parsed = AdKeys.parseTestDeviceIds("$hash, ${hash.lowercase()},00000000-0000-4000-8000-000000000000,,SHORT,${hash}00")
        assertEquals(listOf(hash), parsed)
    }

    @Test
    fun `a GAID is never accepted as a test device id`() {
        // AdMob silently ignores a GAID in setTestDeviceIds, which once served real ads to QA phones for weeks.
        assertTrue(AdKeys.parseTestDeviceIds("00000000-0000-4000-8000-000000000000").isEmpty())
    }

    @Test
    fun `privacy policy url is https`() {
        assertTrue(AdKeys.PRIVACY_POLICY_URL.startsWith("https://"))
    }
}
