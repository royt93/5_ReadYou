package com.mckimquyen.reader.infrastructure.audio

/**
 * Decides which callers are allowed to browse/control CommuteCast through
 * [CommuteMediaSessionService]'s [android.support.v4.media.MediaBrowserServiceCompat] surface
 * (Android Auto, Android Automotive OS, or the app itself).
 *
 * ponytail: name-only allow-list, không verify signing cert của caller. Một app side-load cùng
 * package name (không thể publish trùng tên trên Play Store) về lý thuyết vẫn qua được. Nâng cấp:
 * đọc SHA-256 cert hash từ XML resource và verify qua PackageManager (như Google Universal Android
 * Music Player sample's `PackageValidator`) nếu app phát hành rộng rãi cho nhiều thiết bị lạ.
 */
object CommuteAutoPackageValidator {

    /** Android Auto (chạy trên điện thoại, chiếu ra màn hình xe qua USB/wireless). */
    private const val PKG_ANDROID_AUTO = "com.google.android.projection.gearhead"

    /** Android Automotive OS (chạy thẳng trên xe, không cần điện thoại). */
    private const val PKG_AUTOMOTIVE_OS_HOST = "com.google.android.apps.automotive.templates.host"

    /** Car Media Center trên một số bản Android Automotive OS/AAOS emulator. */
    private const val PKG_CAR_MEDIA = "com.android.car.media"

    private val TRUSTED_EXTERNAL_PACKAGES = setOf(
        PKG_ANDROID_AUTO,
        PKG_AUTOMOTIVE_OS_HOST,
        PKG_CAR_MEDIA,
    )

    /**
     * @param callingPackage tên package của caller (từ `onGetRoot(clientPackageName, ...)`).
     * @param ownPackage tên package của chính app (`context.packageName`) — luôn được tin cậy vì
     *   là chính mình.
     */
    fun isTrusted(callingPackage: String?, ownPackage: String): Boolean {
        if (callingPackage.isNullOrBlank()) return false
        return callingPackage == ownPackage || callingPackage in TRUSTED_EXTERNAL_PACKAGES
    }
}
