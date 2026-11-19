package com.kotlinbitcoins.web.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons of the app, drawn here (24 × 24, 2 px round strokes) so that no icon library is bundled. Always
 * decorative: the text next to an icon is its name, so they are used with a null content description.
 */
object AppIcons {
    val Home = icon("M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Deposit = icon("M12 3v12m0 0l-4-4m4 4l4-4M4 20h16")
    val Purchase = icon("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 8v8M8 12h8")
    val Investments = icon("M5 20V10M12 20V4M19 20v-7")
    val Redemption = icon("M12 15V3m0 0L8 7m4-4l4 4M4 20h16")
    val Statement = icon("M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01")
    val Market = icon("M3 16l5-5 4 3 8-8M3 21h18")
    val Logout = icon("M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16l4-4-4-4M14 12H4")
    val Pause = icon("M9 5v14M15 5v14")
    val Play = icon("M8 5l11 7-11 7z")
    val ArrowUp = icon("M12 19V5m0 0l-6 6m6-6l6 6")
    val ArrowDown = icon("M12 5v14m0 0l-6-6m6 6l6-6")
    val Check = icon("M5 12.5l4.5 4.5L19 7.5")
    val Reinvestment = icon("M4 12a8 8 0 0 1 14-5.3L20 9M20 4v5h-5M20 12a8 8 0 0 1-14 5.3L4 15M4 20v-5h5")
    val ThemeSystem = icon("M4 4h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M8 21h8M12 17v4")
    val ThemeLight =
        icon(
            "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z",
            "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
        )
    val ThemeDark = icon("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Bitcoin =
        icon(
            "M9 6h4.5a3 3 0 0 1 0 6H9zM9 12h5.5a3 3 0 0 1 0 6H9z",
            "M9 6v12M11 4v2M14 4v2M11 18v2M14 18v2",
        )

    private const val SIZE = 24f
    private const val STROKE = 2f

    private fun icon(vararg paths: String): ImageVector {
        val builder =
            ImageVector.Builder(
                defaultWidth = SIZE.dp,
                defaultHeight = SIZE.dp,
                viewportWidth = SIZE,
                viewportHeight = SIZE,
            )
        paths.forEach { path ->
            builder.addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
