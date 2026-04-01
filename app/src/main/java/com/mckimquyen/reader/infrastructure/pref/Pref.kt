package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope

sealed class Pref {

    abstract fun put(context: Context, scope: CoroutineScope)
}

fun Preferences.toSettings(): Settings {
    return Settings(
        // Version
        newVersionNumber = NewVersionNumberPref.fromPreferences(this),
        skipVersionNumber = SkipVersionNumberPref.fromPreferences(this),
        newVersionPublishDate = NewVersionPublishDatePref.fromPreferences(this),
        newVersionLog = NewVersionLogPref.fromPreferences(this),
        newVersionSize = NewVersionSizePref.fromPreferences(this),
        newVersionDownloadUrl = NewVersionDownloadUrlPref.fromPreferences(this),

        // Theme
        themeIndex = ThemeIndexPref.fromPreferences(this),
        customPrimaryColor = CustomPrimaryColorPref.fromPreferences(this),
        darkTheme = DarkThemePref.fromPreferences(this),
        amoledDarkTheme = AmoledDarkThemePref.fromPreferences(this),
        basicFonts = BasicFontsPref.fromPreferences(this),

        // Feeds page
        feedsFilterBarStyle = FeedsFilterBarStylePref.fromPreferences(this),
        feedsFilterBarFilled = FeedsFilterBarFilledPref.fromPreferences(this),
        feedsFilterBarPadding = FeedsFilterBarPaddingPref.fromPreferences(this),
        feedsFilterBarTonalElevation = FeedsFilterBarTonalElevationPref.fromPreferences(this),
        feedsTopBarTonalElevation = FeedsTopBarTonalElevationPref.fromPreferences(this),
        feedsGroupListExpand = FeedsGroupListExpandPref.fromPreferences(this),
        feedsGroupListTonalElevation = FeedsGroupListTonalElevationPref.fromPreferences(this),

        // Flow page
        flowFilterBarStyle = FlowFilterBarStylePref.fromPreferences(this),
        flowFilterBarFilled = FlowFilterBarFilledPref.fromPreferences(this),
        flowFilterBarPadding = FlowFilterBarPaddingPref.fromPreferences(this),
        flowFilterBarTonalElevation = FlowFilterBarTonalElevationPref.fromPreferences(this),
        flowTopBarTonalElevation = FlowTopBarTonalElevationPref.fromPreferences(this),
        flowArticleListFeedIcon = FlowArticleListFeedIconPref.fromPreferences(this),
        flowArticleListFeedName = FlowArticleListFeedNamePref.fromPreferences(this),
        flowArticleListImage = FlowArticleListImagePref.fromPreferences(this),
        flowArticleListDesc = FlowArticleListDescPref.fromPreferences(this),
        flowArticleListTime = FlowArticleListTimePref.fromPreferences(this),
        flowArticleListDateStickyHeader = FlowArticleListDateStickyHeaderPref.fromPreferences(
            this
        ),
        flowArticleListTonalElevation = FlowArticleListTonalElevationPref.fromPreferences(this),

        // Reading page
        readingTheme = ReadingThemePref.fromPreferences(this),
        readingDarkTheme = ReadingDarkThemePref.fromPreferences(this),
        readingPageTonalElevation = ReadingPageTonalElevationPref.fromPreferences(this),
        readingAutoHideToolbar = ReadingAutoHideToolbarPreference.fromPreferences(this),
        readingTextFontSize = ReadingTextFontSizePref.fromPreferences(this),
        readingLetterSpacing = ReadingLetterSpacingPref.fromPreferences(this),
        readingTextHorizontalPadding = ReadingTextHorizontalPaddingPref.fromPreferences(this),
        readingTextAlign = ReadingTextAlignPref.fromPreferences(this),
        readingTextBold = ReadingTextBoldPref.fromPreferences(this),
        readingTitleAlign = ReadingTitleAlignPref.fromPreferences(this),
        readingSubheadAlign = ReadingSubheadAlignPref.fromPreferences(this),
        readingFonts = ReadingFontsPref.fromPreferences(this),
        readingTitleBold = ReadingTitleBoldPref.fromPreferences(this),
        readingSubheadBold = ReadingSubheadBoldPref.fromPreferences(this),
        readingTitleUpperCase = ReadingTitleUpperCasePref.fromPreferences(this),
        readingSubheadUpperCase = ReadingSubheadUpperCasePref.fromPreferences(this),
        readingImageHorizontalPadding = ReadingImageHorizontalPaddingPref.fromPreferences(this),
        readingImageRoundedCorners = ReadingImageRoundedCornersPref.fromPreferences(this),
        readingImageMaximize = ReadingImageMaximizePref.fromPreferences(this),

        // Interaction
        initialPage = InitialPagePref.fromPreferences(this),
        initialFilter = InitialFilterPref.fromPreferences(this),
        openLink = OpenLinkPref.fromPreferences(this),
        openLinkSpecificBrowser = OpenLinkSpecificBrowserPref.fromPreferences(this),
        autoTts = AutoTtsPref.fromPreferences(this),

        // Languages
        languages = LanguagesPref.fromPreferences(this),
    )
}
