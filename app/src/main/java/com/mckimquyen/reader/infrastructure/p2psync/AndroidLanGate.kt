package com.mckimquyen.reader.infrastructure.p2psync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mckimquyen.reader.domain.p2psync.LanAccess
import com.mckimquyen.reader.domain.p2psync.LanGate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** True below Android 17 (nothing to ask) and, from 17, only while the runtime permission is actually granted. */
@Singleton
class AndroidLanGate @Inject constructor(
    @ApplicationContext private val context: Context,
) : LanGate {
    override fun granted(): Boolean =
        !LanAccess.required(Build.VERSION.SDK_INT) ||
            ContextCompat.checkSelfPermission(context, LanAccess.PERMISSION) == PackageManager.PERMISSION_GRANTED
}
