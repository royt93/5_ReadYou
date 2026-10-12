package com.mckimquyen.reader.ui.component.reader

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.domain.model.bilingual.BilingualLanguages
import com.mckimquyen.reader.domain.model.bilingual.CachingParagraphTranslator
import com.mckimquyen.reader.domain.model.bilingual.ParagraphFilter
import com.mckimquyen.reader.domain.model.bilingual.ParagraphTranslator
import com.mckimquyen.reader.domain.model.bilingual.TranslationResult
import com.mckimquyen.reader.infrastructure.pref.LocalReadingBilingual
import com.mckimquyen.reader.infrastructure.pref.LocalReadingBilingualTarget
import com.mckimquyen.reader.infrastructure.translate.MlKitParagraphTranslator

const val BILINGUAL_TRANSLATION_TAG = "bilingualTranslation"

/**
 * App-wide translator (one cache, one set of ML Kit clients shared by every article). It lives as long as
 * the process, so its native translators are intentionally never closed. Tests replace it through
 * [LocalParagraphTranslator].
 */
private val appTranslator: ParagraphTranslator by lazy { CachingParagraphTranslator(MlKitParagraphTranslator()) }

val LocalParagraphTranslator = compositionLocalOf { appTranslator }

/** Translation shown under a paragraph when Bilingual Reader is on. Renders nothing otherwise. */
@Composable
fun BilingualParagraph(original: String, modifier: Modifier = Modifier) {
    if (!LocalReadingBilingual.current.value) return
    val target = BilingualLanguages.resolveTarget(
        LocalReadingBilingualTarget.current.value,
        LocalConfiguration.current.locales[0],
    ) ?: return
    if (!ParagraphFilter.shouldTranslate(original)) return

    val translator = LocalParagraphTranslator.current
    // Keyed on the text and target: leaving the composition (article changed, scrolled away, setting off)
    // cancels the request; the caching translator makes a cancelled request harmless.
    val result by produceState<TranslationResult?>(null, original, target, translator) {
        value = translator.translate(original, target)
    }
    val translated = (result as? TranslationResult.Translated)?.text ?: return
    Text(
        text = translated,
        style = bodyStyle(),
        fontStyle = FontStyle.Italic,
        modifier = modifier.padding(top = 4.dp).testTag(BILINGUAL_TRANSLATION_TAG),
    )
}
