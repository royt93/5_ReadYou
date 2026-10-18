package com.mckimquyen.reader.ui.ext

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NamedNavArgument
import androidx.navigation.compose.composable

private const val ENTER_DURATION_MS = 220
private const val ENTER_DELAY_MS = 90
private const val EXIT_DURATION_MS = 90
private const val ENTER_INITIAL_SCALE = 0.92f

internal fun defaultEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(ENTER_DURATION_MS, delayMillis = ENTER_DELAY_MS)) +
            scaleIn(
                initialScale = ENTER_INITIAL_SCALE,
                animationSpec = tween(ENTER_DURATION_MS, delayMillis = ENTER_DELAY_MS)
            )

internal fun defaultExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(EXIT_DURATION_MS))

/** E-Ink panels ghost on motion, so app-owned navigation transitions become instant. */
internal fun navEnterTransition(eInk: Boolean): EnterTransition =
    if (eInk) EnterTransition.None else defaultEnterTransition()

internal fun navExitTransition(eInk: Boolean): ExitTransition =
    if (eInk) ExitTransition.None else defaultExitTransition()

fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    eInk: Boolean = false,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    enterTransition = { navEnterTransition(eInk) },
    exitTransition = { navExitTransition(eInk) },
    popEnterTransition = { navEnterTransition(eInk) },
    popExitTransition = { navExitTransition(eInk) },
    content = content
)
