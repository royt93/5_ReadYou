package com.mckimquyen.reader.infrastructure.quote

import android.graphics.Bitmap
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec

/** Port so the ViewModel does not call [DualCardRenderer] directly (suspend lets tests fake a slow render). */
fun interface DualBitmapRenderer {
    suspend fun render(spec: DualPerspectiveSpec): Bitmap
}
