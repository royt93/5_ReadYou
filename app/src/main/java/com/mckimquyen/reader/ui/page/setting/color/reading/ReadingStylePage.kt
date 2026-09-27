package com.mckimquyen.reader.ui.page.setting.color.reading

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Segment
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.LocalReadingAutoHideToolbar
import com.mckimquyen.reader.infrastructure.pref.LocalReadingVideoAutoplay
import com.mckimquyen.reader.infrastructure.pref.not
import com.mckimquyen.reader.infrastructure.pref.LocalReadingDarkTheme
import com.mckimquyen.reader.infrastructure.pref.LocalReadingFonts
import com.mckimquyen.reader.infrastructure.pref.LocalReadingPageTonalElevation
import com.mckimquyen.reader.infrastructure.pref.LocalReadingTheme
import com.mckimquyen.reader.infrastructure.pref.ReadingFontsPref
import com.mckimquyen.reader.infrastructure.pref.ReadingPageTonalElevationPref
import com.mckimquyen.reader.infrastructure.pref.ReadingThemePref
import com.mckimquyen.reader.infrastructure.pref.not
import com.mckimquyen.reader.ui.component.ReadingThemePrev
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.BaseSwitch
import com.mckimquyen.reader.ui.component.base.RadioDlg
import com.mckimquyen.reader.ui.component.base.RadioDialogOption
import com.mckimquyen.reader.ui.component.base.Subtitle
import com.mckimquyen.reader.ui.ext.ExternalFonts
import com.mckimquyen.reader.ui.page.common.RouteName
import com.mckimquyen.reader.ui.page.setting.SettingItem
import com.mckimquyen.reader.ui.theme.palette.onLight

@Composable
fun ReadingStylePage(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val readingTheme = LocalReadingTheme.current
    val darkTheme = LocalReadingDarkTheme.current
    val darkThemeNot = !darkTheme
    val tonalElevation = LocalReadingPageTonalElevation.current
    val fonts = LocalReadingFonts.current
    val autoHideToolbar = LocalReadingAutoHideToolbar.current
    val videoAutoplay = LocalReadingVideoAutoplay.current

    var tonalElevationDialogVisible by remember { mutableStateOf(false) }
    var fontsDialogVisible by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            ExternalFonts(context, it, ExternalFonts.FontType.ReadingFont).copyToInternalStorage()
            ReadingFontsPref.External.put(context, scope)
        }
    }

    BaseScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface
            ) {
                navController.popBackStack()
            }
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.reading_page), desc = "")
                }

                // Preview
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.width(24.dp))
                        ReadingThemePref.values.map {
                            if (readingTheme == ReadingThemePref.Custom || it != ReadingThemePref.Custom) {
                                ReadingThemePrev(selected = readingTheme, theme = it) {
                                    it.put(context, scope)
                                    it.applyTheme(context, scope)
                                }
                            } else {
                                Spacer(modifier = Modifier.width(150.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Spacer(modifier = Modifier.width((24 - 8).dp))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                MaterialTheme.colorScheme.inverseOnSurface
                                        onLight MaterialTheme.colorScheme.surface.copy(0.7f)
                            )
                            .clickable { },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // General
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.general)
                    )
                    SettingItem(
                        title = stringResource(R.string.reading_fonts),
                        desc = fonts.toDesc(context),
                        onClick = { fontsDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.dark_reading_theme),
                        desc = darkTheme.toDesc(context),
                        separatedActions = true,
                        onClick = {
                            navController.navigate(RouteName.READING_DARK_THEME) {
                                launchSingleTop = true
                            }
                        },
                    ) {
                        BaseSwitch(
                            activated = darkTheme.isDarkTheme()
                        ) {
                            darkThemeNot.put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.bionic_reading),
                        separatedActions = true,
                        enable = false,
                        onClick = {
//                            (!articleListDesc).put(context, scope)
                        },
                    ) {
                        BaseSwitch(
                            activated = false,
                            enable = false,
                        ) {
//                            (!articleListDesc).put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.auto_hide_toolbars),
                        onClick = {
                            (!autoHideToolbar).put(context, scope)
                        },
                    ) {
                        BaseSwitch(activated = autoHideToolbar.value) {
                            (!autoHideToolbar).put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.reading_video_autoplay),
                        desc = stringResource(R.string.reading_video_autoplay_desc),
                        onClick = {
                            (!videoAutoplay).put(context, scope)
                        },
                    ) {
                        BaseSwitch(activated = videoAutoplay.value) {
                            (!videoAutoplay).put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.rearrange_buttons),
                        enable = false,
                        onClick = {},
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.tonal_elevation),
                        desc = "${tonalElevation.value}dp",
                        onClick = {
                            tonalElevationDialogVisible = true
                        },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Advanced
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.advanced)
                    )
                    SettingItem(
                        title = stringResource(R.string.title),
                        desc = stringResource(R.string.title_desc),
                        icon = Icons.Rounded.Title,
                        onClick = {
                            navController.navigate(RouteName.READING_PAGE_TITLE) {
                                launchSingleTop = true
                            }
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.text),
                        desc = stringResource(R.string.text_desc),
                        icon = Icons.Rounded.Segment,
                        onClick = {
                            navController.navigate(RouteName.READING_PAGE_TEXT) {
                                launchSingleTop = true
                            }
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.images),
                        desc = stringResource(R.string.images_desc),
                        icon = Icons.Outlined.Image,
                        onClick = {
                            navController.navigate(RouteName.READING_PAGE_IMAGE) {
                                launchSingleTop = true
                            }
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.videos),
                        desc = stringResource(R.string.videos_desc),
                        icon = Icons.Outlined.Movie,
                        enable = false,
                        onClick = {
//                            navController.navigate(RouteName.READING_PAGE_VIDEO) {
//                                launchSingleTop = true
//                            }
                        },
                    ) {}
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    RadioDlg(
        visible = tonalElevationDialogVisible,
        title = stringResource(R.string.tonal_elevation),
        options = ReadingPageTonalElevationPref.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == tonalElevation,
            ) {
                it.put(context, scope)
            }
        }
    ) {
        tonalElevationDialogVisible = false
    }

    RadioDlg(
        visible = fontsDialogVisible,
        title = stringResource(R.string.reading_fonts),
        options = ReadingFontsPref.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                style = TextStyle(fontFamily = it.asFontFamily(context)),
                selected = it == fonts,
            ) {
                if (it.value == ReadingFontsPref.External.value) {
                    launcher.launch("*/*")
                } else {
                    it.put(context, scope)
                }
            }
        }
    ) {
        fontsDialogVisible = false
    }
}
