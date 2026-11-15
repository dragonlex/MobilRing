package com.example.MobilRing24KRSK

import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

/**
 * Делает иконки в статус-баре и панели навигации тёмными.
 * Нужно потому, что фон у нас светлый: стандартные белые иконки на нём не видны.
 * Вызывается в onCreate каждого экрана.
 */
fun ComponentActivity.applyLightSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
}
