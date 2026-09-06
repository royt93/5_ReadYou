package com.mckimquyen.reader.infrastructure.pref

import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.map
import com.mckimquyen.reader.domain.model.general.Version
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.ext.dataStore

@Keep
data class Settings(
    // Version
    val newVersionNumber: Version = NewVersionNumberPref.default,
    val skipVersionNumber: Version = SkipVersionNumberPref.default,
    val newVersionPublishDate: String = NewVersionPublishDatePref.default,
    val newVersionLog: String = NewVersionLogPref.default,
    val newVersionSize: String = NewVersionSizePref.default,
    val newVersionDownloadUrl: String = NewVersionDownloadUrlPref.default,

    // Theme
    val themeIndex: Int = ThemeIndexPref.default,
    val customPrimaryColor: String = CustomPrimaryColorPref.default,
    val darkTheme: DarkThemePref = DarkThemePref.default,
    val amoledDarkTheme: AmoledDarkThemePref = AmoledDarkThemePref.default,
    val amoledUnlocked: AmoledUnlockedPref = AmoledUnlockedPref.default,
    val basicFonts: BasicFontsPref = BasicFontsPref.default,

    // Feeds page
    val feedsFilterBarStyle: FeedsFilterBarStylePref = FeedsFilterBarStylePref.default,
    val feedsFilterBarFilled: FeedsFilterBarFilledPref = FeedsFilterBarFilledPref.default,
    val feedsFilterBarPadding: Int = FeedsFilterBarPaddingPref.default,
    val feedsFilterBarTonalElevation: FeedsFilterBarTonalElevationPref = FeedsFilterBarTonalElevationPref.default,
    val feedsTopBarTonalElevation: FeedsTopBarTonalElevationPref = FeedsTopBarTonalElevationPref.default,
    val feedsGroupListExpand: FeedsGroupListExpandPref = FeedsGroupListExpandPref.default,
    val feedsGroupListTonalElevation: FeedsGroupListTonalElevationPref = FeedsGroupListTonalElevationPref.default,

    // Flow page
    val flowFilterBarStyle: FlowFilterBarStylePref = FlowFilterBarStylePref.default,
    val flowFilterBarFilled: FlowFilterBarFilledPref = FlowFilterBarFilledPref.default,
    val flowFilterBarPadding: Int = FlowFilterBarPaddingPref.default,
    val flowFilterBarTonalElevation: FlowFilterBarTonalElevationPref = FlowFilterBarTonalElevationPref.default,
    val flowTopBarTonalElevation: FlowTopBarTonalElevationPref = FlowTopBarTonalElevationPref.default,
    val flowArticleListFeedIcon: FlowArticleListFeedIconPref = FlowArticleListFeedIconPref.default,
    val flowArticleListFeedName: FlowArticleListFeedNamePref = FlowArticleListFeedNamePref.default,
    val flowArticleListImage: FlowArticleListImagePref = FlowArticleListImagePref.default,
    val flowArticleListDesc: FlowArticleListDescPref = FlowArticleListDescPref.default,
    val flowArticleListTime: FlowArticleListTimePref = FlowArticleListTimePref.default,
    val flowArticleListDateStickyHeader: FlowArticleListDateStickyHeaderPref = FlowArticleListDateStickyHeaderPref.default,
    val flowArticleListTonalElevation: FlowArticleListTonalElevationPref = FlowArticleListTonalElevationPref.default,
    val flowStoryClustering: FlowStoryClusteringPref = FlowStoryClusteringPref.default,
    val flowSemanticSearch: FlowSemanticSearchPref = FlowSemanticSearchPref.default,

    // Reading page
    val readingTheme: ReadingThemePref = ReadingThemePref.default,
    val readingDarkTheme: ReadingDarkThemePref = ReadingDarkThemePref.default,
    val readingPageTonalElevation: ReadingPageTonalElevationPref = ReadingPageTonalElevationPref.default,
    val readingAutoHideToolbar: ReadingAutoHideToolbarPreference = ReadingAutoHideToolbarPreference.default,
    val readingTextFontSize: Int = ReadingTextFontSizePref.default,
    val readingLetterSpacing: Double = ReadingLetterSpacingPref.default,
    val readingTextHorizontalPadding: Int = ReadingTextHorizontalPaddingPref.default,
    val readingTextAlign: ReadingTextAlignPref = ReadingTextAlignPref.default,
    val readingTextBold: ReadingTextBoldPref = ReadingTextBoldPref.default,
    val readingTitleAlign: ReadingTitleAlignPref = ReadingTitleAlignPref.default,
    val readingSubheadAlign: ReadingSubheadAlignPref = ReadingSubheadAlignPref.default,
    val readingFonts: ReadingFontsPref = ReadingFontsPref.default,
    val readingTitleBold: ReadingTitleBoldPref = ReadingTitleBoldPref.default,
    val readingSubheadBold: ReadingSubheadBoldPref = ReadingSubheadBoldPref.default,
    val readingTitleUpperCase: ReadingTitleUpperCasePref = ReadingTitleUpperCasePref.default,
    val readingSubheadUpperCase: ReadingSubheadUpperCasePref = ReadingSubheadUpperCasePref.default,
    val readingImageHorizontalPadding: Int = ReadingImageHorizontalPaddingPref.default,
    val readingImageRoundedCorners: Int = ReadingImageRoundedCornersPref.default,
    val readingImageMaximize: ReadingImageMaximizePref = ReadingImageMaximizePref.default,

    // Interaction
    val initialPage: InitialPagePref = InitialPagePref.default,
    val initialFilter: InitialFilterPref = InitialFilterPref.default,
    val openLink: OpenLinkPref = OpenLinkPref.default,
    val openLinkSpecificBrowser: OpenLinkSpecificBrowserPref = OpenLinkSpecificBrowserPref.default,
    val autoTts: AutoTtsPref = AutoTtsPref.default,

    // Languages
    val languages: LanguagesPref = LanguagesPref.default,
)

// Version
val LocalNewVersionNumber = compositionLocalOf { NewVersionNumberPref.default }
val LocalSkipVersionNumber = compositionLocalOf { SkipVersionNumberPref.default }
val LocalNewVersionPublishDate = compositionLocalOf { NewVersionPublishDatePref.default }
val LocalNewVersionLog = compositionLocalOf { NewVersionLogPref.default }
val LocalNewVersionSize = compositionLocalOf { NewVersionSizePref.default }
val LocalNewVersionDownloadUrl = compositionLocalOf { NewVersionDownloadUrlPref.default }

// Theme
val LocalThemeIndex =
    compositionLocalOf { ThemeIndexPref.default }
val LocalCustomPrimaryColor =
    compositionLocalOf { CustomPrimaryColorPref.default }
val LocalDarkTheme =
    compositionLocalOf<DarkThemePref> { DarkThemePref.default }
val LocalAmoledDarkTheme =
    compositionLocalOf<AmoledDarkThemePref> { AmoledDarkThemePref.default }
val LocalAmoledUnlocked =
    compositionLocalOf<AmoledUnlockedPref> { AmoledUnlockedPref.default }
val LocalBasicFonts = compositionLocalOf<BasicFontsPref> { BasicFontsPref.default }

// Feeds page
val LocalFeedsFilterBarStyle =
    compositionLocalOf<FeedsFilterBarStylePref> { FeedsFilterBarStylePref.default }
val LocalFeedsFilterBarFilled =
    compositionLocalOf<FeedsFilterBarFilledPref> { FeedsFilterBarFilledPref.default }
val LocalFeedsFilterBarPadding =
    compositionLocalOf { FeedsFilterBarPaddingPref.default }
val LocalFeedsFilterBarTonalElevation =
    compositionLocalOf<FeedsFilterBarTonalElevationPref> { FeedsFilterBarTonalElevationPref.default }
val LocalFeedsTopBarTonalElevation =
    compositionLocalOf<FeedsTopBarTonalElevationPref> { FeedsTopBarTonalElevationPref.default }
val LocalFeedsGroupListExpand =
    compositionLocalOf<FeedsGroupListExpandPref> { FeedsGroupListExpandPref.default }
val LocalFeedsGroupListTonalElevation =
    compositionLocalOf<FeedsGroupListTonalElevationPref> { FeedsGroupListTonalElevationPref.default }

// Flow page
val LocalFlowFilterBarStyle =
    compositionLocalOf<FlowFilterBarStylePref> { FlowFilterBarStylePref.default }
val LocalFlowFilterBarFilled =
    compositionLocalOf<FlowFilterBarFilledPref> { FlowFilterBarFilledPref.default }
val LocalFlowFilterBarPadding =
    compositionLocalOf { FlowFilterBarPaddingPref.default }
val LocalFlowFilterBarTonalElevation =
    compositionLocalOf<FlowFilterBarTonalElevationPref> { FlowFilterBarTonalElevationPref.default }
val LocalFlowTopBarTonalElevation =
    compositionLocalOf<FlowTopBarTonalElevationPref> { FlowTopBarTonalElevationPref.default }
val LocalFlowArticleListFeedIcon =
    compositionLocalOf<FlowArticleListFeedIconPref> { FlowArticleListFeedIconPref.default }
val LocalFlowArticleListFeedName =
    compositionLocalOf<FlowArticleListFeedNamePref> { FlowArticleListFeedNamePref.default }
val LocalFlowArticleListImage =
    compositionLocalOf<FlowArticleListImagePref> { FlowArticleListImagePref.default }
val LocalFlowArticleListDesc =
    compositionLocalOf<FlowArticleListDescPref> { FlowArticleListDescPref.default }
val LocalFlowArticleListTime =
    compositionLocalOf<FlowArticleListTimePref> { FlowArticleListTimePref.default }
val LocalFlowArticleListDateStickyHeader =
    compositionLocalOf<FlowArticleListDateStickyHeaderPref> { FlowArticleListDateStickyHeaderPref.default }
val LocalFlowArticleListTonalElevation =
    compositionLocalOf<FlowArticleListTonalElevationPref> { FlowArticleListTonalElevationPref.default }
val LocalFlowStoryClustering =
    compositionLocalOf<FlowStoryClusteringPref> { FlowStoryClusteringPref.default }
val LocalFlowSemanticSearch =
    compositionLocalOf<FlowSemanticSearchPref> { FlowSemanticSearchPref.default }

// Reading page
val LocalReadingTheme = compositionLocalOf<ReadingThemePref> { ReadingThemePref.default }
val LocalReadingDarkTheme = compositionLocalOf<ReadingDarkThemePref> { ReadingDarkThemePref.default }
val LocalReadingPageTonalElevation =
    compositionLocalOf<ReadingPageTonalElevationPref> { ReadingPageTonalElevationPref.default }
val LocalReadingAutoHideToolbar =
    compositionLocalOf<ReadingAutoHideToolbarPreference> { ReadingAutoHideToolbarPreference.default }
val LocalReadingTextFontSize = compositionLocalOf { ReadingTextFontSizePref.default }
val LocalReadingLetterSpacing = compositionLocalOf { ReadingLetterSpacingPref.default }
val LocalReadingTextHorizontalPadding = compositionLocalOf { ReadingTextHorizontalPaddingPref.default }
val LocalReadingTextAlign = compositionLocalOf<ReadingTextAlignPref> { ReadingTextAlignPref.default }
val LocalReadingTextBold = compositionLocalOf<ReadingTextBoldPref> { ReadingTextBoldPref.default }
val LocalReadingTitleAlign = compositionLocalOf<ReadingTitleAlignPref> { ReadingTitleAlignPref.default }
val LocalReadingSubheadAlign =
    compositionLocalOf<ReadingSubheadAlignPref> { ReadingSubheadAlignPref.default }
val LocalReadingFonts = compositionLocalOf<ReadingFontsPref> { ReadingFontsPref.default }
val LocalReadingTitleBold = compositionLocalOf<ReadingTitleBoldPref> { ReadingTitleBoldPref.default }
val LocalReadingSubheadBold =
    compositionLocalOf<ReadingSubheadBoldPref> { ReadingSubheadBoldPref.default }
val LocalReadingTitleUpperCase =
    compositionLocalOf<ReadingTitleUpperCasePref> { ReadingTitleUpperCasePref.default }
val LocalReadingSubheadUpperCase =
    compositionLocalOf<ReadingSubheadUpperCasePref> { ReadingSubheadUpperCasePref.default }
val LocalReadingImageHorizontalPadding = compositionLocalOf { ReadingImageHorizontalPaddingPref.default }
val LocalReadingImageRoundedCorners = compositionLocalOf { ReadingImageRoundedCornersPref.default }
val LocalReadingImageMaximize =
    compositionLocalOf<ReadingImageMaximizePref> { ReadingImageMaximizePref.default }

// Interaction
val LocalInitialPage = compositionLocalOf<InitialPagePref> { InitialPagePref.default }
val LocalInitialFilter =
    compositionLocalOf<InitialFilterPref> { InitialFilterPref.default }
val LocalOpenLink =
    compositionLocalOf<OpenLinkPref> { OpenLinkPref.default }
val LocalOpenLinkSpecificBrowser =
    compositionLocalOf { OpenLinkSpecificBrowserPref.default }
val LocalAutoTts = compositionLocalOf<AutoTtsPref> { AutoTtsPref.default }

// Languages
val LocalLanguages =
    compositionLocalOf<LanguagesPref> { LanguagesPref.default }

@Composable
fun SettingsProvider(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember {
        context.dataStore.data.map {
            Log.i("RLog", "AppTheme: ${it}")
            it.toSettings()
        }
    }.collectAsStateValue(initial = Settings())

    CompositionLocalProvider(
        // Version
        LocalNewVersionNumber provides settings.newVersionNumber,
        LocalSkipVersionNumber provides settings.skipVersionNumber,
        LocalNewVersionPublishDate provides settings.newVersionPublishDate,
        LocalNewVersionLog provides settings.newVersionLog,
        LocalNewVersionSize provides settings.newVersionSize,
        LocalNewVersionDownloadUrl provides settings.newVersionDownloadUrl,
        LocalBasicFonts provides settings.basicFonts,

        // Theme
        LocalThemeIndex provides settings.themeIndex,
        LocalCustomPrimaryColor provides settings.customPrimaryColor,
        LocalDarkTheme provides settings.darkTheme,
        LocalAmoledDarkTheme provides settings.amoledDarkTheme,
        LocalAmoledUnlocked provides settings.amoledUnlocked,
        LocalBasicFonts provides settings.basicFonts,

        // Feeds page
        LocalFeedsTopBarTonalElevation provides settings.feedsTopBarTonalElevation,
        LocalFeedsGroupListExpand provides settings.feedsGroupListExpand,
        LocalFeedsGroupListTonalElevation provides settings.feedsGroupListTonalElevation,
        LocalFeedsFilterBarStyle provides settings.feedsFilterBarStyle,
        LocalFeedsFilterBarFilled provides settings.feedsFilterBarFilled,
        LocalFeedsFilterBarPadding provides settings.feedsFilterBarPadding,
        LocalFeedsFilterBarTonalElevation provides settings.feedsFilterBarTonalElevation,

        // Flow page
        LocalFlowTopBarTonalElevation provides settings.flowTopBarTonalElevation,
        LocalFlowArticleListFeedIcon provides settings.flowArticleListFeedIcon,
        LocalFlowArticleListFeedName provides settings.flowArticleListFeedName,
        LocalFlowArticleListImage provides settings.flowArticleListImage,
        LocalFlowArticleListDesc provides settings.flowArticleListDesc,
        LocalFlowArticleListTime provides settings.flowArticleListTime,
        LocalFlowArticleListDateStickyHeader provides settings.flowArticleListDateStickyHeader,
        LocalFlowArticleListTonalElevation provides settings.flowArticleListTonalElevation,
        LocalFlowStoryClustering provides settings.flowStoryClustering,
        LocalFlowSemanticSearch provides settings.flowSemanticSearch,
        LocalFlowFilterBarStyle provides settings.flowFilterBarStyle,
        LocalFlowFilterBarFilled provides settings.flowFilterBarFilled,
        LocalFlowFilterBarPadding provides settings.flowFilterBarPadding,
        LocalFlowFilterBarTonalElevation provides settings.flowFilterBarTonalElevation,

        // Reading page
        LocalReadingTheme provides settings.readingTheme,
        LocalReadingDarkTheme provides settings.readingDarkTheme,
        LocalReadingPageTonalElevation provides settings.readingPageTonalElevation,
        LocalReadingAutoHideToolbar provides settings.readingAutoHideToolbar,
        LocalReadingTextFontSize provides settings.readingTextFontSize,
        LocalReadingLetterSpacing provides settings.readingLetterSpacing,
        LocalReadingTextHorizontalPadding provides settings.readingTextHorizontalPadding,
        LocalReadingTextAlign provides settings.readingTextAlign,
        LocalReadingTextBold provides settings.readingTextBold,
        LocalReadingTitleAlign provides settings.readingTitleAlign,
        LocalReadingSubheadAlign provides settings.readingSubheadAlign,
        LocalReadingFonts provides settings.readingFonts,
        LocalReadingTitleBold provides settings.readingTitleBold,
        LocalReadingSubheadBold provides settings.readingSubheadBold,
        LocalReadingTitleUpperCase provides settings.readingTitleUpperCase,
        LocalReadingSubheadUpperCase provides settings.readingSubheadUpperCase,
        LocalReadingImageHorizontalPadding provides settings.readingImageHorizontalPadding,
        LocalReadingImageRoundedCorners provides settings.readingImageRoundedCorners,
        LocalReadingImageMaximize provides settings.readingImageMaximize,

        // Interaction
        LocalInitialPage provides settings.initialPage,
        LocalInitialFilter provides settings.initialFilter,
        LocalOpenLink provides settings.openLink,
        LocalOpenLinkSpecificBrowser provides settings.openLinkSpecificBrowser,
        LocalAutoTts provides settings.autoTts,

        // Languages
        LocalLanguages provides settings.languages,
    ) {
        content()
    }
}
