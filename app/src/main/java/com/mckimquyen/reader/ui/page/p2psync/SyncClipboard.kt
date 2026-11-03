package com.mckimquyen.reader.ui.page.p2psync

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/** Copies one sync value; the pairing secret is flagged sensitive so Android 13+ hides the clipboard preview. */
fun Context.copySyncText(text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("sync", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    manager.setPrimaryClip(clip)
}
