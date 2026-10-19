package com.mckimquyen.reader.infrastructure.quote

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.getFileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.random.Random

/** Ghi PNG vào cacheDir/quote_cards và dựng intent chia sẻ qua FileProvider có sẵn (cache-path "."). */
class QuoteCardSharer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun writePng(
        bitmap: Bitmap,
        template: QuoteCardTemplate,
        nowMillis: Long = System.currentTimeMillis(),
    ): File = withContext(ioDispatcher) {
        val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.lastModified() < nowMillis - RETENTION_MS) it.delete() }
        val file = File(dir, "quote_${template.storageName}_${nowMillis}_${Random.nextInt(0, NAME_RANDOM_BOUND)}.png")
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "PNG compress failed" }
        }
        file
    }

    fun buildShareIntent(file: File, chooserTitle: String): Intent {
        val uri = FileProvider.getUriForFile(context, context.getFileProvider(), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_PNG
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Chooser được mở bằng application context nên cần NEW_TASK.
        return Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val DIR_NAME = "quote_cards"
        const val RETENTION_MS = 24L * 60 * 60 * 1000
        const val MIME_PNG = "image/png"
        private const val PNG_QUALITY = 100
        private const val NAME_RANDOM_BOUND = 100_000
    }
}
