package com.mckimquyen.reader.ui.page.common

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeEntryNavigationTest {
    @Test fun notificationRouteTakesPrecedenceOverConfiguredInitialPage() {
        assertEquals(RouteName.BRAIN_RPG, resolveInitialRoute(RouteName.BRAIN_RPG, initialPage = 1))
    }

    @Test fun noNotificationRoute_keepsConfiguredInitialPageBehavior() {
        assertEquals(RouteName.FLOW, resolveInitialRoute(null, initialPage = 1))
        assertEquals(null, resolveInitialRoute(null, initialPage = 0))
    }

    @Test fun archiveQueryRouteIsDistinctFromEveryOtherRoute() {
        val routes = RouteName::class.java.declaredFields
            .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.apply { isAccessible = true }.get(null) as String }
        assertEquals("archive_query", RouteName.ARCHIVE_QUERY)
        assertEquals(1, routes.count { it == RouteName.ARCHIVE_QUERY })
    }

    @Test fun readingRouteOpensTheExactArticleId() {
        assertEquals("${RouteName.READING}/art-7", readingRoute("art-7"))
    }
}
