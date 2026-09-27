package com.mckimquyen.reader.ui.ext

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

/**
 * Lets `WindowInsets` reach the content of a Compose [androidx.compose.ui.window.Dialog].
 *
 * A Dialog gets its own window, and by default the platform allocates that window's bounds inside
 * the "stable" content area only — the area already excluding the status bar, cutout and
 * navigation bar — regardless of `decorFitsSystemWindows`. `setDecorFitsSystemWindows(false)` stops
 * the decor view from padding for insets it does receive, but the window is still capped to that
 * stable rectangle, so every inset it can report is zero: there is no unsafe area inside its own
 * bounds to describe. [WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS] is what lifts that cap and
 * lets the window's bounds extend under the system bars in the first place; only once the window
 * actually reaches under them do `safeDrawingPadding()` and friends have anything real to measure.
 *
 * Flipping these alone is not enough, either: the window was already attached (and, on some OS
 * versions, already measured) under the old configuration, and nothing re-dispatches insets to it
 * just because a flag changed after that. [ViewCompat.requestApplyInsets] is what asks the platform
 * to redeliver them under the new bounds — without it the dialog can sit correctly configured
 * forever while every `WindowInsets` composable inside still reads the stale zero from its first
 * measurement pass.
 *
 * Call this once at the top of a full-bleed dialog's content.
 */
@Composable
fun DialogEdgeToEdge() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        ViewCompat.requestApplyInsets(view)
    }
}
