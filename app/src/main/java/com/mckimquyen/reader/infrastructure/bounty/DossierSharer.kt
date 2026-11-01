package com.mckimquyen.reader.infrastructure.bounty

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.getFileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Writes the dossier Markdown to cacheDir/dossiers and builds a share intent through the existing FileProvider. */
class DossierSharer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun write(
        markdown: String,
        query: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): File = withContext(ioDispatcher) {
        val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.lastModified() < nowMillis - RETENTION_MS) it.delete() }
        File(dir, "dossier_${slugOf(query)}_$nowMillis.md").apply { writeText(markdown, Charsets.UTF_8) }
    }

    fun buildShareIntent(file: File, chooserTitle: String): Intent {
        val uri = FileProvider.getUriForFile(context, context.getFileProvider(), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_MARKDOWN
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // The chooser is launched from an application context, so it needs NEW_TASK.
        return Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Lowercase ASCII `[a-z0-9_]`, max [MAX_SLUG_LENGTH] chars; no path separators or dots can survive. */
    private fun slugOf(query: String): String =
        query.lowercase()
            .replace(NON_SLUG_CHARS, "_")
            .trim('_')
            .take(MAX_SLUG_LENGTH)
            .trim('_')
            .ifEmpty { FALLBACK_SLUG }

    companion object {
        const val DIR_NAME = "dossiers"
        const val RETENTION_MS = 24L * 60 * 60 * 1000
        const val MIME_MARKDOWN = "text/markdown"
        private const val MAX_SLUG_LENGTH = 40
        private const val FALLBACK_SLUG = "query"
        private val NON_SLUG_CHARS = Regex("[^a-z0-9]+")
    }
}
