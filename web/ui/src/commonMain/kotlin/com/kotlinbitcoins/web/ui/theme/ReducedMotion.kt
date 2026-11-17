package com.kotlinbitcoins.web.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * `prefers-reduced-motion` (FR-045): with `true`, navigation transitions are `None`, indeterminate progress
 * indicators become the static text "Carregando…" and values are not animated.
 */
val LocalReducedMotion = compositionLocalOf { false }
