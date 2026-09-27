package com.mckimquyen.reader.ui.component.reader

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mckimquyen.reader.infrastructure.pref.LocalReadingFonts
import com.mckimquyen.reader.infrastructure.pref.LocalReadingImageHorizontalPadding
import com.mckimquyen.reader.infrastructure.pref.LocalReadingImageRoundedCorners
import com.mckimquyen.reader.infrastructure.pref.LocalReadingLetterSpacing
import com.mckimquyen.reader.infrastructure.pref.LocalReadingSubheadAlign
import com.mckimquyen.reader.infrastructure.pref.LocalReadingSubheadBold
import com.mckimquyen.reader.infrastructure.pref.LocalReadingTextAlign
import com.mckimquyen.reader.infrastructure.pref.LocalReadingTextBold
import com.mckimquyen.reader.infrastructure.pref.LocalReadingTextFontSize
import com.mckimquyen.reader.infrastructure.pref.LocalReadingTextHorizontalPadding
import com.mckimquyen.reader.ui.ext.alphaLN

const val MAX_CONTENT_WIDTH = 840.0

/** Player box shape before the real video size is known; 16:9 matches most web video. */
const val VIDEO_ASPECT_RATIO = 16f / 9f

@Stable
@Composable
@ReadOnlyComposable
fun imageHorizontalPadding(): Int =
    LocalReadingImageHorizontalPadding.current

@Stable
@Composable
@ReadOnlyComposable
fun imageShape(): RoundedCornerShape =
    RoundedCornerShape(LocalReadingImageRoundedCorners.current.dp)

@Stable
@Composable
@ReadOnlyComposable
fun onSurfaceColor(): Color =
    MaterialTheme.colorScheme.onSurface

@Stable
@Composable
@ReadOnlyComposable
fun onSurfaceVariantColor(): Color =
    MaterialTheme.colorScheme.onSurfaceVariant

@Stable
@Composable
@ReadOnlyComposable
fun textHorizontalPadding(): Int =
    LocalReadingTextHorizontalPadding.current

@Stable
@Composable
@ReadOnlyComposable
fun bodyForeground(): Color = onSurfaceVariantColor()

@Stable
@Composable
@ReadOnlyComposable
fun bodyStyle(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        fontFamily = LocalReadingFonts.current.asFontFamily(LocalContext.current),
        fontWeight = if (LocalReadingTextBold.current.value) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = LocalReadingTextFontSize.current.sp,
        letterSpacing = LocalReadingLetterSpacing.current.sp,
        color = bodyForeground(),
        textAlign = LocalReadingTextAlign.current.toTextAlign(),
    )
)

@Stable
@Composable
@ReadOnlyComposable
fun h1Style(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        fontFamily = LocalReadingFonts.current.asFontFamily(LocalContext.current),
        fontWeight = if (LocalReadingSubheadBold.current.value) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 28.sp,
        letterSpacing = 0.sp,
        color = onSurfaceColor(),
        textAlign = LocalReadingSubheadAlign.current.toTextAlign(),
    )
)

@Stable
@Composable
@ReadOnlyComposable
fun h2Style(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        fontFamily = LocalReadingFonts.current.asFontFamily(LocalContext.current),
        fontWeight = if (LocalReadingSubheadBold.current.value) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 28.sp,
        letterSpacing = 0.sp,
        color = onSurfaceColor(),
        textAlign = LocalReadingSubheadAlign.current.toTextAlign(),
    )
)

@Stable
@Composable
@ReadOnlyComposable
fun h3Style(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        fontFamily = LocalReadingFonts.current.asFontFamily(LocalContext.current),
        fontWeight = if (LocalReadingSubheadBold.current.value) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 19.sp,
        letterSpacing = 0.sp,
        color = onSurfaceColor(),
        textAlign = LocalReadingSubheadAlign.current.toTextAlign(),
    )
)

@Stable
@Composable
@ReadOnlyComposable
fun h4Style(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        fontFamily = LocalReadingFonts.current.asFontFamily(LocalContext.current),
        fontWeight = if (LocalReadingSubheadBold.current.value) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 17.sp,
        letterSpacing = 0.sp,
        color = onSurfaceColor(),
        textAlign = LocalReadingSubheadAlign.current.toTextAlign(),
    )
)

@Stable
@Composable
@ReadOnlyComposable
fun h5Style(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        fontFamily = LocalReadingFonts.current.asFontFamily(LocalContext.current),
        fontWeight = if (LocalReadingSubheadBold.current.value) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 17.sp,
        letterSpacing = 0.sp,
        color = onSurfaceColor(),
        textAlign = LocalReadingSubheadAlign.current.toTextAlign(),
    )
)

@Stable
@Composable
@ReadOnlyComposable
fun h6Style(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        fontFamily = LocalReadingFonts.current.asFontFamily(LocalContext.current),
        fontWeight = if (LocalReadingSubheadBold.current.value) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 17.sp,
        letterSpacing = 0.sp,
        color = onSurfaceColor(),
        textAlign = LocalReadingSubheadAlign.current.toTextAlign(),
    )
)

@Stable
@Composable
@ReadOnlyComposable
fun captionStyle(): TextStyle = LocalTextStyle.current.merge(
    MaterialTheme.typography.bodySmall.merge(
        TextStyle(
            fontFamily = LocalReadingFonts.current.asFontFamily(LocalContext.current),
            color = bodyForeground().copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    )
)

@Stable
@Composable
@ReadOnlyComposable
fun linkTextStyle(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        fontFamily = LocalReadingFonts.current.asFontFamily(LocalContext.current),
        fontSize = LocalReadingTextFontSize.current.sp,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
)

@Stable
@Composable
fun codeBlockStyle(): TextStyle =
    MaterialTheme.typography.titleSmall.merge(
        SpanStyle(
            color = bodyForeground(),
            fontFamily = FontFamily.Monospace
        )
    )

@Stable
@Composable
fun codeBlockBackground(): Color =
    MaterialTheme.colorScheme.secondary.copy(alpha = (0.dp).alphaLN(weight = 3.2f))

@Stable
@Composable
fun boldStyle(): TextStyle =
    bodyStyle().merge(
        SpanStyle(
            fontWeight = FontWeight.SemiBold,
            color = onSurfaceColor(),
        )
    )

@Stable
@Composable
fun codeInlineStyle(): SpanStyle =
    MaterialTheme.typography.titleSmall.toSpanStyle().merge(
        SpanStyle(
            color = bodyForeground(),
            fontFamily = FontFamily.Monospace,
        )
    )
