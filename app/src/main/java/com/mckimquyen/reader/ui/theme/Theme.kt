package com.mckimquyen.reader.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.mckimquyen.reader.infrastructure.pref.DarkThemePref
import com.mckimquyen.reader.infrastructure.pref.LocalBasicFonts
import com.mckimquyen.reader.infrastructure.pref.LocalDarkTheme
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode
import com.mckimquyen.reader.infrastructure.pref.LocalThemeIndex
import com.mckimquyen.reader.ui.theme.palette.LocalTonalPalettes
import com.mckimquyen.reader.ui.theme.palette.TonalPalettes
import com.mckimquyen.reader.ui.theme.palette.core.ProvideZcamViewingConditions
import com.mckimquyen.reader.ui.theme.palette.dynamic.extractTonalPalettesFromUserWallpaper
import com.mckimquyen.reader.ui.theme.palette.dynamicDarkColorScheme
import com.mckimquyen.reader.ui.theme.palette.dynamicLightColorScheme

@Composable
fun AppTheme(
    useDarkTheme: Boolean,
    wallpaperPalettes: List<TonalPalettes> = extractTonalPalettesFromUserWallpaper(),
    content: @Composable () -> Unit,
) {
    val themeIndex = LocalThemeIndex.current
    val eInkEnabled = LocalEInkMode.current.value

    val tonalPalettes = wallpaperPalettes[
        if (themeIndex >= wallpaperPalettes.size) {
            when {
                wallpaperPalettes.size == 5 -> 0
                wallpaperPalettes.size > 5 -> 5
                else -> 0
            }
        } else {
            themeIndex
        }
    ]

    ProvideZcamViewingConditions {
        CompositionLocalProvider(
            LocalTonalPalettes provides tonalPalettes.apply { Preparing() },
            LocalTextStyle provides LocalTextStyle.current.applyTextDirection(),
            // E-Ink is always light: helpers such as onLight/onDark must not pick dark tones, but the
            // saved dark-theme preference itself is never rewritten.
            LocalDarkTheme provides if (eInkEnabled) DarkThemePref.OFF else LocalDarkTheme.current,
        ) {
            MaterialTheme(
                colorScheme = when {
                    eInkEnabled -> eInkLightColorScheme()
                    useDarkTheme -> dynamicDarkColorScheme()
                    else -> dynamicLightColorScheme()
                },
                typography = LocalBasicFonts.current.asTypography(LocalContext.current)
                    .applyTextDirection(),
                shapes = Shapes,
                content = content,
            )
        }
    }
}
