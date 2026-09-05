package com.mckimquyen.reader.ui.component

import android.os.Build
import android.view.SoundEffectConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.mckimquyen.reader.domain.model.general.Filter
import com.mckimquyen.reader.infrastructure.pref.FlowFilterBarStylePref
import com.mckimquyen.reader.infrastructure.pref.LocalThemeIndex
import com.mckimquyen.reader.ui.ext.surfaceColorAtElevation
import com.mckimquyen.reader.ui.theme.palette.onDark

@Composable
fun FilterBar(
    filter: Filter,
    filterBarStyle: Int,
    filterBarFilled: Boolean,
    filterBarPadding: Dp,
    filterBarTonalElevation: Dp,
    filterOnClick: (Filter) -> Unit = {},
) {
    val view = LocalView.current
    val themeIndex = LocalThemeIndex.current

    NavigationBar(
        modifier = Modifier,
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(filterBarTonalElevation),
        tonalElevation = filterBarTonalElevation,
    ) {
        Spacer(modifier = Modifier.width(filterBarPadding))
        Filter.values.forEach { item ->
            NavigationBarItem(
//                        modifier = Modifier.height(60.dp),
                alwaysShowLabel = when (filterBarStyle) {
                    FlowFilterBarStylePref.Icon.value -> false
                    FlowFilterBarStylePref.IconLabel.value -> true
                    FlowFilterBarStylePref.IconLabelOnlySelected.value -> false
                    else -> false
                },
                icon = {
                    Icon(
                        imageVector = if (filter == item && filterBarFilled) {
                            item.iconFilled
                        } else {
                            item.iconOutline
                        },
                        contentDescription = item.toName()
                    )
                },
                label = if (filterBarStyle == FlowFilterBarStylePref.Icon.value) {
                    null
                } else {
                    {
                        Text(
                            text = item.toName(),
//                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                selected = filter == item,
                onClick = {
//                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                    filterOnClick(item)
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = if (themeIndex == 5 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    } onDark MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        Spacer(modifier = Modifier.width(filterBarPadding))
    }
}
