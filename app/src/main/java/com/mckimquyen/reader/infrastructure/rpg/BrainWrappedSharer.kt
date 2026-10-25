package com.mckimquyen.reader.infrastructure.rpg

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.getFileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.random.Random

class BrainWrappedSharer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun writePng(
        bitmap: Bitmap,
        nowMillis: Long = System.currentTimeMillis(),
    ): File = withContext(ioDispatcher) {
        val directory = File(context.cacheDir, DIR_NAME).apply {
            check(isDirectory || mkdirs()) { "Unable to create Brain Wrapped cache directory" }
        }
        directory.listFiles()?.forEach { file ->
            if (file.lastModified() < nowMillis - RETENTION_MS) file.delete()
        }
        val output = File(directory, "brain_wrapped_${nowMillis}_${Random.nextInt(0, RANDOM_BOUND)}.png")
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)) { "PNG compression failed" }
        }
        output
    }

    fun buildShareIntent(file: File, chooserTitle: String): Intent {
        require(file.isFile && file.parentFile == File(context.cacheDir, DIR_NAME)) {
            "Brain Wrapped share file must be inside its cache directory"
        }
        val uri = FileProvider.getUriForFile(context, context.getFileProvider(), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_PNG
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle)
    }

    companion object {
        const val DIR_NAME = "brain_wrapped"
        const val MIME_PNG = "image/png"
        const val RETENTION_MS = 24L * 60 * 60 * 1000
        private const val PNG_QUALITY = 100
        private const val RANDOM_BOUND = 100_000
    }
}
