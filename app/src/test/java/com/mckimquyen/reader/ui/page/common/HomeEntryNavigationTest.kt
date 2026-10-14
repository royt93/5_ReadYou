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
}
