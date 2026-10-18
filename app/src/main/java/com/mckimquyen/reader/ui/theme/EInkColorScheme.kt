package com.mckimquyen.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val EInkWhite = Color.White
private val EInkBlack = Color.Black
private val EInkGrayDark = Color(0xFF404040)
private val EInkGray = Color(0xFF606060)
private val EInkGrayLight = Color(0xFFE0E0E0)
private val EInkGrayLighter = Color(0xFFE8E8E8)
private val EInkGraySurface = Color(0xFFF0F0F0)
private val EInkGrayOutline = Color(0xFF505050)
private val EInkGrayOutlineVariant = Color(0xFF707070)
private val EInkErrorContainer = Color(0xFFEEEEEE)

fun eInkLightColorScheme(): ColorScheme = lightColorScheme(
    primary = EInkBlack,
    onPrimary = EInkWhite,
    primaryContainer = EInkGrayLight,
    onPrimaryContainer = EInkBlack,
    inversePrimary = EInkGrayDark,
    secondary = EInkGrayDark,
    onSecondary = EInkWhite,
    secondaryContainer = Color(0xFFDDDDDD),
    onSecondaryContainer = EInkBlack,
    tertiary = EInkGray,
    onTertiary = EInkWhite,
    tertiaryContainer = EInkGrayLighter,
    onTertiaryContainer = EInkBlack,
    background = EInkWhite,
    onBackground = EInkBlack,
    surface = EInkWhite,
    onSurface = EInkBlack,
    surfaceVariant = EInkGraySurface,
    onSurfaceVariant = Color(0xFF303030),
    surfaceTint = EInkBlack,
    inverseSurface = EInkBlack,
    inverseOnSurface = EInkWhite,
    outline = EInkGrayOutline,
    outlineVariant = EInkGrayOutlineVariant,
    error = EInkBlack,
    onError = EInkWhite,
    errorContainer = EInkErrorContainer,
    onErrorContainer = EInkBlack,
)
