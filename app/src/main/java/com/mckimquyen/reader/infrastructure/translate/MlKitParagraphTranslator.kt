package com.mckimquyen.reader.infrastructure.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.mckimquyen.reader.domain.model.bilingual.ClosableLruPool
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
    // Bounded: one native translator per source>target pair would otherwise grow with every language met.
    private val translators = ClosableLruPool<String, Translator>(MAX_TRANSLATORS) { pair ->
        val (source, target) = pair.split(PAIR_SEPARATOR)
        Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
    }

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

    private fun translatorFor(source: String, target: String): Translator = translators.get("$source$PAIR_SEPARATOR$target")

    @Synchronized
    override fun close() {
        translators.closeAll()
        identifier.close()
    }

    private companion object {
        const val UNDETERMINED_LANGUAGE = "und"
        const val PAIR_SEPARATOR = ">"
        const val MAX_TRANSLATORS = 3
    }
}
