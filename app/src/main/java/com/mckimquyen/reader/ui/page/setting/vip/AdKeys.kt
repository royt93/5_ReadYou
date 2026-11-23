package com.mckimquyen.reader.ui.page.setting.vip

import com.mckimquyen.reader.BuildConfig

/**
 * Ad/VIP configuration that comes from the private app.properties (via BuildConfig); nothing sensitive is written in source.
 *
 * - `VIP_KEY_SECRET` only protects the SDK's own preferences against tampering. It does NOT verify codes any more:
 *   codes are ECDSA tokens (public key below) or redeem codes (`VIP_REDEEM_CODES`), checked by the SDK.
 */
object AdKeys {

    /** Privacy Policy URL: public, read straight from BuildConfig. */
    const val PRIVACY_POLICY_URL: String = BuildConfig.PRIVACY_POLICY_URL

    /** Integrity secret for the SDK preferences (at least 16 characters with upper case, digit and special character). */
    val VIP_SECRET: String get() = BuildConfig.VIP_KEY_SECRET

    /** ECDSA P-256 public key (base64url) that verifies VIP tokens; the private key never leaves myKeyStore. */
    val VIP_TOKEN_PUBLIC_KEY: String get() = BuildConfig.VIP_TOKEN_PUBLIC_KEY

    /** `CODE:days,CODE:days` from the private file, parsed once; blank or malformed entries are dropped. */
    val VIP_REDEEM_CODES: Map<String, Int> get() = parseRedeemCodes(BuildConfig.VIP_REDEEM_CODES)

    /** Test-device hashes (AdMob), from the private file; used for debug and release alike. */
    val TEST_DEVICE_IDS: List<String> get() = parseTestDeviceIds(BuildConfig.ADMOB_TEST_DEVICE_IDS)

    internal const val MIN_REDEEM_DAYS = 1
    internal const val MAX_REDEEM_DAYS = 365

    internal fun parseRedeemCodes(raw: String): Map<String, Int> =
        raw.split(',')
            .mapNotNull { entry ->
                val parts = entry.trim().split(':')
                if (parts.size != 2) return@mapNotNull null
                val code = parts[0].trim()
                val days = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
                if (code.isEmpty() || days !in MIN_REDEEM_DAYS..MAX_REDEEM_DAYS) null else code to days
            }
            .toMap()

    private val TEST_DEVICE_HASH = Regex("^[0-9A-F]{32}$")

    /** Only the 32-hex hash Google prints in logcat is valid; a GAID (UUID with dashes) is silently ignored by AdMob, so it is dropped here. */
    internal fun parseTestDeviceIds(raw: String): List<String> =
        raw.split(',').map { it.trim().uppercase() }.filter { TEST_DEVICE_HASH.matches(it) }.distinct()
}
