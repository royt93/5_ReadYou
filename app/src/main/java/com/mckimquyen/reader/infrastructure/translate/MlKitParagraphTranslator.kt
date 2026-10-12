package com.mckimquyen.reader.infrastructure.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.mckimquyen.reader.domain.model.bilingual.ParagraphTranslator
import com.mckimquyen.reader.domain.model.bilingual.TranslationResult
import kotlinx.coroutines.tasks.await
import java.io.Closeable

/**
 * On-device translation through ML Kit. The source language is detected per paragraph; a paragraph that is
 * already in the target language (or whose language cannot be determined) yields [TranslationResult.SameLanguage].
 *
 * Language models (~30 MB each) are downloaded on first use over any network (user decision) and then work
 * offline. The first paragraph in a new language therefore waits for the download; later ones are fast.
 * Call [close] when the owner goes away to release the native translators.
 */
class MlKitParagraphTranslator : ParagraphTranslator, Closeable {

    private val identifier: LanguageIdentifier = LanguageIdentification.getClient()
    private val translators = HashMap<String, Translator>()

    override suspend fun translate(text: String, target: String): TranslationResult {
        val detected = identifier.identifyLanguage(text).await()
        if (detected == UNDETERMINED_LANGUAGE) return TranslationResult.SameLanguage
        val source = TranslateLanguage.fromLanguageTag(detected) ?: return TranslationResult.SameLanguage
        val targetTag = TranslateLanguage.fromLanguageTag(target) ?: return TranslationResult.Failed
        if (source == targetTag) return TranslationResult.SameLanguage

        val translator = translatorFor(source, targetTag)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        return TranslationResult.Translated(translator.translate(text).await())
    }

    @Synchronized
    private fun translatorFor(source: String, target: String): Translator =
        translators.getOrPut("$source>$target") {
            Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
            )
        }

    @Synchronized
    override fun close() {
        translators.values.forEach(Translator::close)
        translators.clear()
        identifier.close()
    }

    private companion object {
        const val UNDETERMINED_LANGUAGE = "und"
    }
}
