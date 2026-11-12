package com.example.btcwidget.ui.social

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** An icon in its two states, as on X: [outline] normally, [filled] for the page that's open. */
class NavIcon(val outline: ImageVector, val filled: ImageVector) {
    fun of(selected: Boolean) = if (selected) filled else outline
}

/**
 * The icons of the bottom bar, the top bar, the side panel and Settings: one family (Material
 * shapes, Apache 2.0; the Bitcoin coin drawn for Block), so a page has the same icon wherever
 * it appears. An object, so they build on first use.
 */
object NavIcons {
    private const val HOUSE = "M4,10.2L10.6,4.9Q12,3.8 13.4,4.9L20,10.2V19a1.5,1.5 0,0 1,-1.5 1.5H15v-5.5h-6v5.5H5.5A1.5,1.5 0,0 1,4 19z"
    private const val PHONE = "M8,3h8a3,3 0,0 1,3 3v12a3,3 0,0 1,-3 3h-8a3,3 0,0 1,-3 -3V6a3,3 0,0 1,3 -3z"
    private const val PLAY = "M10.3,9.1v5.8a0.6,0.6 0,0 0,0.9 0.5l4.5,-2.9a0.6,0.6 0,0 0,0 -1l-4.5,-2.9a0.6,0.6 0,0 0,-0.9 0.5z"
    private const val BUBBLE = "M7,4.5h10a3.5,3.5 0,0 1,3.5 3.5v6a3.5,3.5 0,0 1,-3.5 3.5h-5.5l-4.5,3.5v-3.5a3.5,3.5 0,0 1,-3.5 -3.5v-6a3.5,3.5 0,0 1,3.5 -3.5z"
    private sealed class Part(val d: String) {
        class Fill(d: String) : Part(d)
        class EvenOdd(d: String) : Part(d)
        class Stroke(d: String, val width: Float) : Part(d)
    }

    private fun icon(name: String, vararg parts: Part): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        parts.forEach { p ->
            when (p) {
                is Part.Fill -> b.addPath(pathData = addPathNodes(p.d), fill = SolidColor(androidx.compose.ui.graphics.Color.Black))
                is Part.EvenOdd -> b.addPath(pathData = addPathNodes(p.d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(androidx.compose.ui.graphics.Color.Black))
                is Part.Stroke -> b.addPath(
                    pathData = addPathNodes(p.d), fill = null, stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
                    strokeLineWidth = p.width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
                )
            }
        }
        return b.build()
    }

    val Bell = NavIcon(
        icon("Bell", Part.Fill("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32L13.5,4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2zM16,17L8,17v-6c0,-2.48 1.51,-4.5 4,-4.5s4,2.02 4,4.5v6z")),
        icon("BellFilled", Part.Fill("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"))
    )
    val Bookmarks = NavIcon(
        icon("Bookmarks", Part.Fill("M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z")),
        icon("BookmarksFilled", Part.Fill("M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z"))
    )
    val Bitcoin = NavIcon(
        icon("Bitcoin", Part.Stroke("M12,2.8a9.2,9.2 0 1,0 0.001,0z", 1.9f), Part.Fill("M9.94,6.42 h1.24 v1.24 h0.62 V6.42 h1.24 v1.3 c1.43,0.248,2.17,1.12,2.17,2.23 c0,0.744,-0.372,1.36,-0.992,1.67 c0.868,0.31,1.43,0.992,1.43,1.92 c0,1.43,-1.12,2.42,-2.6,2.67 V17.6 h-1.24 v-1.24 h-0.62 v1.24 H9.94 v-1.24 H8.08 v-1.24 h0.93 V8.9 H8.08 V7.66 h1.86 V6.42 zM10.2,8.9 v2.48 h2.48 c0.682,0,1.18,-0.558,1.18,-1.24 s-0.496,-1.24,-1.18,-1.24 H10.2 zM10.2,12.6 v2.48 h2.85 c0.744,0,1.3,-0.558,1.3,-1.24 s-0.558,-1.24,-1.3,-1.24 H10.2 z")),
        icon("BitcoinFilled", Part.EvenOdd("M12,1.5a10.5,10.5 0 1,0 0.001,0zM9.94,6.42 h1.24 v1.24 h0.62 V6.42 h1.24 v1.3 c1.43,0.248,2.17,1.12,2.17,2.23 c0,0.744,-0.372,1.36,-0.992,1.67 c0.868,0.31,1.43,0.992,1.43,1.92 c0,1.43,-1.12,2.42,-2.6,2.67 V17.6 h-1.24 v-1.24 h-0.62 v1.24 H9.94 v-1.24 H8.08 v-1.24 h0.93 V8.9 H8.08 V7.66 h1.86 V6.42 zM10.2,8.9 v2.48 h2.48 c0.682,0,1.18,-0.558,1.18,-1.24 s-0.496,-1.24,-1.18,-1.24 H10.2 zM10.2,12.6 v2.48 h2.85 c0.744,0,1.3,-0.558,1.3,-1.24 s-0.558,-1.24,-1.3,-1.24 H10.2 z"))
    )
    val PriceAlerts = NavIcon(
        icon("PriceAlerts", Part.Stroke("M3,17.5l6,-6 4,4 7.5,-7.5M15,8h5.5v5.5", 1.9f)),
        icon("PriceAlertsFilled", Part.Stroke("M3,17.5l6,-6 4,4 7.5,-7.5M15,8h5.5v5.5", 2.9f))
    )
    val Settings = NavIcon(
        icon("Settings", Part.Fill("M19.43,12.98c0.04,-0.32 0.07,-0.64 0.07,-0.98 0,-0.34 -0.03,-0.66 -0.07,-0.98l2.11,-1.65c0.19,-0.15 0.24,-0.42 0.12,-0.64l-2,-3.46c-0.09,-0.16 -0.26,-0.25 -0.44,-0.25 -0.06,0 -0.12,0.01 -0.17,0.03l-2.49,1c-0.52,-0.4 -1.08,-0.73 -1.69,-0.98l-0.38,-2.65C14.46,2.18 14.25,2 14,2h-4c-0.25,0 -0.46,0.18 -0.49,0.42l-0.38,2.65c-0.61,0.25 -1.17,0.59 -1.69,0.98l-2.49,-1c-0.06,-0.02 -0.12,-0.03 -0.18,-0.03 -0.17,0 -0.34,0.09 -0.43,0.25l-2,3.46c-0.13,0.22 -0.07,0.49 0.12,0.64l2.11,1.65c-0.04,0.32 -0.07,0.65 -0.07,0.98 0,0.33 0.03,0.66 0.07,0.98l-2.11,1.65c-0.19,0.15 -0.24,0.42 -0.12,0.64l2,3.46c0.09,0.16 0.26,0.25 0.44,0.25 0.06,0 0.12,-0.01 0.17,-0.03l2.49,-1c0.52,0.4 1.08,0.73 1.69,0.98l0.38,2.65c0.03,0.24 0.24,0.42 0.49,0.42h4c0.25,0 0.46,-0.18 0.49,-0.42l0.38,-2.65c0.61,-0.25 1.17,-0.59 1.69,-0.98l2.49,1c0.06,0.02 0.12,0.03 0.18,0.03 0.17,0 0.34,-0.09 0.43,-0.25l2,-3.46c0.12,-0.22 0.07,-0.49 -0.12,-0.64l-2.11,-1.65zM17.45,11.27c0.04,0.31 0.05,0.52 0.05,0.73 0,0.21 -0.02,0.43 -0.05,0.73l-0.14,1.13 0.89,0.7 1.08,0.84 -0.7,1.21 -1.27,-0.51 -1.04,-0.42 -0.9,0.68c-0.43,0.32 -0.84,0.56 -1.25,0.73l-1.06,0.43 -0.16,1.13 -0.2,1.35h-1.4l-0.19,-1.35 -0.16,-1.13 -1.06,-0.43c-0.43,-0.18 -0.83,-0.41 -1.23,-0.71l-0.91,-0.7 -1.06,0.43 -1.27,0.51 -0.7,-1.21 1.08,-0.84 0.89,-0.7 -0.14,-1.13c-0.03,-0.31 -0.05,-0.54 -0.05,-0.74s0.02,-0.43 0.05,-0.73l0.14,-1.13 -0.89,-0.7 -1.08,-0.84 0.7,-1.21 1.27,0.51 1.04,0.42 0.9,-0.68c0.43,-0.32 0.84,-0.56 1.25,-0.73l1.06,-0.43 0.16,-1.13 0.2,-1.35h1.39l0.19,1.35 0.16,1.13 1.06,0.43c0.43,0.18 0.83,0.41 1.23,0.71l0.91,0.7 1.06,-0.43 1.27,-0.51 0.7,1.21 -1.07,0.85 -0.89,0.7 0.14,1.13zM12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM12,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z")),
        icon("SettingsFilled", Part.Fill("M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94 0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"))
    )
    val Widgets = NavIcon(
        icon("Widgets", Part.Fill("M3,3v8h8V3H3zM9,9H5V5h4V9zM3,13v8h8v-8H3zM9,19H5v-4h4V19zM13,3v8h8V3H13zM19,9h-4V5h4V9zM13,13v8h8v-8H13zM19,19h-4v-4h4V19z")),
        icon("WidgetsFilled", Part.Fill("M3,3h8v8H3zM13,3h8v8h-8zM3,13h8v8H3zM13,13h8v8h-8z"))
    )
    val Communities = NavIcon(
        icon("Communities", Part.Fill("M9,13.75c-2.34,0 -7,1.17 -7,3.5L2,19h14v-1.75c0,-2.33 -4.66,-3.5 -7,-3.5zM4.34,17c0.84,-0.58 2.87,-1.25 4.66,-1.25s3.82,0.67 4.66,1.25L4.34,17zM9,12c1.93,0 3.5,-1.57 3.5,-3.5S10.93,5 9,5 5.5,6.57 5.5,8.5 7.07,12 9,12zM9,7c0.83,0 1.5,0.67 1.5,1.5S9.83,10 9,10s-1.5,-0.67 -1.5,-1.5S8.17,7 9,7zM16.04,13.81c1.16,0.84 1.96,1.96 1.96,3.44L18,19h4v-1.75c0,-2.02 -3.5,-3.17 -5.96,-3.44zM15,12c1.93,0 3.5,-1.57 3.5,-3.5S16.93,5 15,5c-0.54,0 -1.04,0.13 -1.5,0.35 0.63,0.89 1,1.98 1,3.15s-0.37,2.26 -1,3.15c0.46,0.22 0.96,0.35 1.5,0.35z")),
        icon("CommunitiesFilled", Part.Fill("M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45V19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z"))
    )
    // A house with a soft, rounded roof (drawn for Block).
    val Home = NavIcon(
        icon("Home", Part.Stroke(HOUSE, 1.9f)),
        icon("HomeFilled", Part.Fill(HOUSE), Part.Stroke(HOUSE, 1.9f))
    )
    val Lists = NavIcon(
        icon("Lists", Part.Fill("M19,5v14H5V5h14m1.1,-2H3.9c-0.5,0 -0.9,0.4 -0.9,0.9v16.2c0,0.4 0.4,0.9 0.9,0.9h16.2c0.4,0 0.9,-0.5 0.9,-0.9V3.9c0,-0.5 -0.5,-0.9 -0.9,-0.9zM11,7h6v2h-6V7zM11,11h6v2h-6v-2zM11,15h6v2h-6zM7,7h2v2H7zM7,11h2v2H7zM7,15h2v2H7z")),
        icon("ListsFilled", Part.EvenOdd("M3.9,3h16.2c0.5,0 0.9,0.4 0.9,0.9v16.2c0,0.5 -0.4,0.9 -0.9,0.9H3.9c-0.5,0 -0.9,-0.4 -0.9,-0.9V3.9C3,3.4 3.4,3 3.9,3zM11,7h6v2h-6zM11,11h6v2h-6zM11,15h6v2h-6zM7,7h2v2H7zM7,11h2v2H7zM7,15h2v2H7z"))
    )
    // A speech bubble, as on the profile's message button (drawn for Block).
    val Messages = NavIcon(
        icon("Messages", Part.Stroke(BUBBLE, 1.9f)),
        icon("MessagesFilled", Part.Fill(BUBBLE), Part.Stroke(BUBBLE, 1.9f))
    )
    // A screen standing on end with a play button: short videos, one at a time (drawn for Block).
    val Shorts = NavIcon(
        icon("Shorts", Part.Stroke(PHONE, 1.9f), Part.Fill(PLAY)),
        icon("ShortsFilled", Part.EvenOdd(PHONE + PLAY), Part.Stroke(PHONE, 1.9f))
    )
    val Profile = NavIcon(
        icon("Profile", Part.Fill("M12,6c1.1,0 2,0.9 2,2s-0.9,2 -2,2 -2,-0.9 -2,-2 0.9,-2 2,-2m0,10c2.7,0 5.8,1.29 6,2H6c0.23,-0.72 3.31,-2 6,-2m0,-12C9.79,4 8,5.79 8,8s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z")),
        icon("ProfileFilled", Part.Fill("M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"))
    )
    val Search = NavIcon(
        icon("Search", Part.Stroke("M10.8,3.8a7,7 0 1,0 0.001,0zM16,16L20.5,20.5", 1.9f)),
        icon("SearchFilled", Part.Stroke("M10.8,3.8a7,7 0 1,0 0.001,0zM16,16L20.5,20.5", 2.9f))
    )
}
