package com.mckimquyen.reader.ui.component.reader

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    // Leaving the composition (article changed, scrolled away, setting off) cancels the request; the caching
    // translator makes a cancelled request harmless.
    // Keyed on text, target and translator: a new key resets to "no result yet" so a stale answer for the
    // old target is never shown, and the old request is cancelled with the effect.
    var result by remember(original, target, translator) { mutableStateOf<TranslationResult?>(null) }
    LaunchedEffect(original, target, translator) {
        result = translator.translate(original, target)
    }
    val translated = (result as? TranslationResult.Translated)?.text ?: return
    Text(
        text = translated,
        style = bodyStyle(),
        fontStyle = FontStyle.Italic,
        modifier = modifier.padding(top = 4.dp).testTag(BILINGUAL_TRANSLATION_TAG),
    )
}
