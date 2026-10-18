package com.mckimquyen.reader.ui.component.base

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode

internal fun extensibleEnterTransition(eInk: Boolean): EnterTransition =
    if (eInk) EnterTransition.None else fadeIn() + expandVertically()

internal fun extensibleExitTransition(eInk: Boolean): ExitTransition =
    if (eInk) ExitTransition.None else fadeOut() + shrinkVertically()

@Composable
fun BaseExtensibleVisibility(
    visible: Boolean,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = extensibleEnterTransition(LocalEInkMode.current.value),
        exit = extensibleExitTransition(LocalEInkMode.current.value),
        content = content,
    )
}
