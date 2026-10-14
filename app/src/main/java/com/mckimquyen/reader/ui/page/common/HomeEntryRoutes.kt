package com.mckimquyen.reader.ui.page.common

/** A notification deep link takes precedence over the user's configured initial page. */
internal fun resolveInitialRoute(notificationRoute: String?, initialPage: Int): String? = when {
    notificationRoute == RouteName.BRAIN_RPG -> RouteName.BRAIN_RPG
    initialPage == INITIAL_PAGE_FLOW -> RouteName.FLOW
    else -> null
}

private const val INITIAL_PAGE_FLOW = 1
