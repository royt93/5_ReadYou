package com.mckimquyen.reader.domain.p2psync

/** Android 17 (API 37) gates LAN traffic behind a runtime permission for apps that target it. */
object LanAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    const val REQUIRED_FROM_SDK = 37

    fun required(sdkInt: Int): Boolean = sdkInt >= REQUIRED_FROM_SDK
}
