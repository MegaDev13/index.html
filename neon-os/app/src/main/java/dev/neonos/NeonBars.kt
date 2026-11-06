package dev.neonos

import android.graphics.Color
import android.os.Build
import android.view.Window
import android.view.WindowInsetsController

@Suppress("DEPRECATION")
internal fun darkNavigation(window: Window) {
    val decor = window.decorView
    window.isNavigationBarContrastEnforced = false
    window.navigationBarColor = if (Build.VERSION.SDK_INT >= 35) Color.TRANSPARENT else Color.BLACK
    window.navigationBarDividerColor = Color.TRANSPARENT
    decor.windowInsetsController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
}
