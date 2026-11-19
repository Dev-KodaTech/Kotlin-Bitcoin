package com.kotlinbitcoins.web.state.port

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeModeTest {
    @Test
    fun systemFollowsTheSystemPreference() {
        assertEquals(false, ThemeMode.System.isDark(systemDark = false))
        assertEquals(true, ThemeMode.System.isDark(systemDark = true))
    }

    @Test
    fun aChosenThemeWinsOverTheSystem() {
        listOf(false, true).forEach { systemDark ->
            assertEquals(false, ThemeMode.Light.isDark(systemDark), "claro com sistema escuro=$systemDark")
            assertEquals(true, ThemeMode.Dark.isDark(systemDark), "escuro com sistema escuro=$systemDark")
        }
    }
}
