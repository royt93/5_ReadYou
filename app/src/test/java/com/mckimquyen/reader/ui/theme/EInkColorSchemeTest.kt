package com.mckimquyen.reader.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class EInkColorSchemeTest {

    @Test
    fun eInkLightColorScheme_usesHighContrastNeutralRoles() {
        val scheme = eInkLightColorScheme()

        assertEquals(Color.White, scheme.background)
        assertEquals(Color.Black, scheme.onBackground)
        assertEquals(Color.White, scheme.surface)
        assertEquals(Color.Black, scheme.onSurface)
        assertEquals(Color.Black, scheme.primary)
        assertEquals(Color.White, scheme.onPrimary)
        assertEquals(Color.Black, scheme.error)
        assertEquals(Color.White, scheme.onError)
    }
}
