package com.mckimquyen.reader.ui.page.setting.acc

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.Subtitle
import com.mckimquyen.reader.ui.page.setting.SettingItem
import com.mckimquyen.reader.ui.page.setting.acc.addition.AddFeverAccountDialog
import com.mckimquyen.reader.ui.page.setting.acc.addition.AddLocalAccountDialog
import com.mckimquyen.reader.ui.page.setting.acc.addition.AdditionViewModel
import com.mckimquyen.reader.ui.theme.palette.onLight

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AddAccountsPage(
    navController: NavHostController = rememberNavController(),
    additionViewModel: AdditionViewModel = hiltViewModel(),
) {
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
                    DisplayText(text = stringResource(R.string.add_accounts), desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.local),
                    )
                    SettingItem(
                        title = stringResource(R.string.local),
                        desc = stringResource(R.string.local_desc),
                        icon = Icons.Rounded.RssFeed,
                        onClick = {
                            additionViewModel.showAddLocalAccountDialog()
                        },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.services),
                    )
                    SettingItem(
                        enable = false,
                        title = stringResource(R.string.feedly),
                        desc = stringResource(R.string.feedly_desc),
                        iconPainter = painterResource(id = R.drawable.ic_feedly),
                        onClick = {},
                    ) {}
                    SettingItem(
                        enable = false,
                        title = stringResource(R.string.inoreader),
                        desc = stringResource(R.string.inoreader_desc),
                        iconPainter = painterResource(id = R.drawable.ic_inoreader),
                        onClick = {},
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.self_hosted),
                    )
                    SettingItem(
                        enable = false,
                        title = stringResource(R.string.fresh_rss),
                        desc = stringResource(R.string.fresh_rss_desc),
                        iconPainter = painterResource(id = R.drawable.ic_freshrss),
                        onClick = {

                        },
                    ) {}
                    SettingItem(
                        enable = false,
                        title = stringResource(R.string.google_reader),
                        desc = stringResource(R.string.google_reader_desc),
                        icon = Icons.Rounded.RssFeed,
                        onClick = {

                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.fever),
                        desc = stringResource(R.string.fever_desc),
                        iconPainter = painterResource(id = R.drawable.ic_fever),
                        onClick = {
                            additionViewModel.showAddFeverAccountDialog()
                        },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    AddLocalAccountDialog(navController)
    AddFeverAccountDialog(navController)
}

@Preview
@Composable
fun AddAccountsPreview() {
    AddAccountsPage()
}
