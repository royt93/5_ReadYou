package com.mckimquyen.reader.ui.page.setting

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mckimquyen.reader.BuildConfig
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.LocalNewVersionNumber
import com.mckimquyen.reader.infrastructure.pref.LocalSkipVersionNumber
import com.mckimquyen.reader.sdkadbmob.AdMobManager
import com.mckimquyen.reader.ui.component.base.Banner
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.ext.getCurrentVersion
import com.mckimquyen.reader.ui.ext.moreApp
import com.mckimquyen.reader.ui.ext.openBrowserPolicy
import com.mckimquyen.reader.ui.ext.openUrlInBrowser
import com.mckimquyen.reader.ui.ext.rateApp
import com.mckimquyen.reader.ui.ext.shareApp
import com.mckimquyen.reader.ui.page.about.AboutActivity
import com.mckimquyen.reader.ui.page.common.RouteName
import com.mckimquyen.reader.ui.page.setting.tip.UpdateDialog
import com.mckimquyen.reader.ui.page.setting.tip.UpdateViewModel
import com.mckimquyen.reader.ui.theme.palette.onLight

@Composable
fun SettingsPage(
    navController: NavHostController,
    updateViewModel: UpdateViewModel = hiltViewModel(),
    activity: Activity,
) {
    val context = LocalContext.current
    val newVersion = LocalNewVersionNumber.current
    val skipVersion = LocalSkipVersionNumber.current
    val currentVersion by remember { mutableStateOf(context.getCurrentVersion()) }

    BaseScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeedbackIconButton(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface
                ) {
                    AdMobManager.showInterstitial(activity) { success ->
                        if (success) {
                            Log.d("roy93~", "Ad đã hiển thị và đóng thành công")
                        } else {
                            Log.d("roy93~", "Ad không hiển thị được hoặc có lỗi")
                        }
                        navController.popBackStack()
                    }
                }

                Spacer(modifier = Modifier.width(4.dp)) // Khoảng cách nhỏ giữa icon và chữ
                Text(
                    text = "(Ads may appear)",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.settings), desc = "")
                }
                item {
                    Box {
                        if (newVersion.whetherNeedUpdate(currentVersion, skipVersion)) {
                            Banner(
                                modifier = Modifier.zIndex(1f),
                                title = stringResource(R.string.get_new_updates),
                                desc = stringResource(
                                    R.string.get_new_updates_desc,
                                    newVersion.toString(),
                                ),
                                icon = Icons.Outlined.Lightbulb,
                                action = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.close),
                                    )
                                },
                            ) {
                                updateViewModel.showDialog()
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        // Banner(
                        //     title = stringResource(R.string.in_coding),
                        //     desc = stringResource(R.string.coming_soon),
                        //     icon = Icons.Outlined.Lightbulb,
                        // )
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = "Version",
                        desc = BuildConfig.VERSION_NAME,
                        icon = Icons.Outlined.Build,
                    ) {
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.accounts),
                        desc = stringResource(R.string.accounts_desc),
                        icon = Icons.Outlined.AccountCircle,
                    ) {
                        navController.navigate(RouteName.ACCOUNTS) {
                            launchSingleTop = true
                        }
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.color_and_style),
                        desc = stringResource(R.string.color_and_style_desc),
                        icon = Icons.Outlined.Palette,
                    ) {
                        navController.navigate(RouteName.COLOR_AND_STYLE) {
                            launchSingleTop = true
                        }
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.interaction),
                        desc = stringResource(R.string.interaction_desc),
                        icon = Icons.Outlined.TouchApp,
                    ) {
                        navController.navigate(RouteName.INTERACTION) {
                            launchSingleTop = true
                        }
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.languages),
                        desc = stringResource(R.string.languages_desc),
                        icon = Icons.Outlined.Language,
                    ) {
                        navController.navigate(RouteName.LANGUAGES) {
                            launchSingleTop = true
                        }
                    }
                }
//                item {
//                    SelectableSettingGroupItem(
//                        title = stringResource(R.string.tips_and_support),
//                        desc = stringResource(R.string.tips_and_support_desc),
//                        icon = Icons.Outlined.TipsAndUpdates,
//                    ) {
//                        navController.navigate(RouteName.TIPS_AND_SUPPORT) {
//                            launchSingleTop = true
//                        }
//                    }
//                }
                item {
                    SelectableSettingGroupItem(
                        title = "Rate app",
                        desc = "Please rate 5 stars if you find this application useful",
                        icon = Icons.Outlined.StarRate,
                    ) {
                        context.rateApp(packageName = context.packageName)
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = "Get more apps",
                        desc = "Download more of these amazing apps from the store, they are very useful for you",
                        icon = Icons.Outlined.Download,
                    ) {
                        context.moreApp()
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = "Share this app",
                        desc = "Please share this application for others to use with you",
                        icon = Icons.Outlined.Share,
                    ) {
                        context.shareApp()
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = "Term & Policy",
                        desc = "Read Security terms and privacy policy",
                        icon = Icons.Outlined.Policy,
                    ) {
                        context.openBrowserPolicy()
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = "About",
                        desc = "Getting to Know this App",
                        icon = Icons.Outlined.Info,
                    ) {
                        context.startActivity(Intent(activity, AboutActivity::class.java))
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = "Source code (original version)",
                        desc = "An Android RSS reader presented in Material You style.",
                        icon = Icons.Outlined.Code,
                    ) {
                        context.openUrlInBrowser("https://github.com/Ashinch/ReadYou")
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = "Source code (this version)",
                        desc = "An Android RSS reader presented in Material You style.",
                        icon = Icons.Outlined.Code,
                    ) {
                        context.openUrlInBrowser("https://github.com/royt93/5_ReadYou")
                    }
                }
                item {
                    SelectableSettingGroupItem(
                        title = "Join the BetaTesting Community Today!",
                        desc = "Test exciting new products and get paid to give feedback for new apps",
                        icon = Icons.Outlined.AddCircle,
                    ) {
//                        context.openUrlInBrowser("https://github.com/gj-loitp/20-TESTER-FOR-CLOSED-TESTING/tree/main")
                        context.rateApp("com.mckimquyen.bemytester")
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    UpdateDialog()
}
