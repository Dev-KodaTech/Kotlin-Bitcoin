package com.kotlinbitcoins.web.e2e.support

/** Viewport of a test (research.md W-17). [deviceScaleFactor] 2 at 640 × 400 is a 1280 px window at 200% zoom. */
data class Viewport(
    val name: String,
    val width: Int,
    val height: Int,
    val deviceScaleFactor: Double = 1.0,
)

object Viewports {
    val PHONE_SMALL = Viewport("320", 320, 640)
    val PHONE = Viewport("375", 375, 812)
    val TABLET = Viewport("768", 768, 1024)
    val DESKTOP = Viewport("1280", 1280, 800)
    val WIDE = Viewport("1920", 1920, 1080)
    val ZOOM_200 = Viewport("zoom-200", 640, 400, deviceScaleFactor = 2.0)

    val ALL = listOf(PHONE_SMALL, PHONE, TABLET, DESKTOP, WIDE, ZOOM_200)
}
