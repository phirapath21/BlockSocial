package com.example.btcwidget.ui.social

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Outline action icons (Material Design shapes, Apache 2.0) for notes and the image viewer, which
 * aren't in the core icon set. An object, so they build on first use.
 */
object Glyphs {
    private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()

    /** Drawn with a line rather than filled, for shapes the core set lacks (the zap bolt). */
    private fun stroked(name: String, path: String, width: Float = 1.8f): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(path), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
    ).build()

    val Zap = stroked("Zap", "M13,2.5L4.5,13.5h6.5l-1,8 8.5,-11h-6.5z")
    val Play = icon("Play", "M8,5v14l11,-7z")
    val Pause = icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    val SoundOn = icon(
        "SoundOn",
        "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"
    )
    val SoundOff = icon(
        "SoundOff",
        "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z"
    )
    val Rotate = icon(
        "Rotate",
        "M16.48,2.52c3.27,1.55 5.61,4.72 5.97,8.48h1.5C23.44,4.84 18.29,0 12,0l-0.66,0.03 3.81,3.81 1.33,-1.32zM10.23,1.75c-0.59,-0.59 -1.54,-0.59 -2.12,0L1.75,8.11c-0.59,0.59 -0.59,1.54 0,2.12l12.02,12.02c0.59,0.59 1.54,0.59 2.12,0l6.36,-6.36c0.59,-0.59 0.59,-1.54 0,-2.12L10.23,1.75zM14.83,21.19L2.81,9.17l6.36,-6.36 12.02,12.02 -6.36,6.36zM7.52,21.48C4.25,19.94 1.91,16.76 1.55,13H0.05C0.56,19.16 5.71,24 12,24l0.66,-0.03 -3.81,-3.81 -1.33,1.32z"
    )
    val Comment = icon("Comment", "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,16H6l-2,2V4h16v12z")
    val Repost = icon("Repost", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z")
    val Like = icon(
        "Like",
        "M16.5,3c-1.74,0 -3.41,0.81 -4.5,2.09C10.91,3.81 9.24,3 7.5,3 4.42,3 2,5.42 2,8.5c0,3.78 3.4,6.86 8.55,11.54L12,21.35l1.45,-1.32C18.6,15.36 22,12.28 22,8.5 22,5.42 19.58,3 16.5,3zM12.1,18.55l-0.1,0.1 -0.1,-0.1C7.14,14.24 4,11.39 4,8.5 4,6.5 5.5,5 7.5,5c1.54,0 3.04,0.99 3.57,2.36h1.87C13.46,5.99 14.96,5 16.5,5c2,0 3.5,1.5 3.5,3.5 0,2.89 -3.14,5.74 -7.9,10.05z"
    )
    val Liked = icon(
        "Liked",
        "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"
    )
    val Bookmark = icon("Bookmark", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z")
    val Bookmarked = icon("Bookmarked", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z")
    val Share = icon(
        "Share",
        "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"
    )
    val Photo = icon(
        "Photo",
        "M19,5v14H5V5h14zM19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM14.14,11.86l-3,3.87L9,13.14 6,17h12l-3.86,-5.14z"
    )
    val Expand = icon("Expand", "M21,11V3h-8l3.29,3.29 -10,10L3,13v8h8l-3.29,-3.29 10,-10z")
    val Back = icon("Back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val More = icon(
        "More",
        "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"
    )

    // For menu sheets.
    val Delete = icon("Delete", "M16,9v10H8V9h8m-1.5,-6h-5l-1,1H5v2h14V4h-3.5l-1,-1zM18,7H6v12c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7z")
    val Flag = icon("Flag", "M14,6l-1,-2H5v17h2v-7h5l1,2h7V6h-6zM18,14h-4l-1,-2H7V6h5l1,2h5v6z")
    val Pin = icon(
        "Pin",
        "M14,4v5c0,1.12 0.37,2.16 1,3H9c0.65,-0.86 1,-1.9 1,-3V4h4M17,2H7c-0.55,0 -1,0.45 -1,1s0.45,1 1,1h1v5c0,1.66 -1.34,3 -3,3v2h5.97v7l1,1 1,-1v-7H19v-2c-1.66,0 -3,-1.34 -3,-3V4h1c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1z"
    )
    val Copy = icon("Copy", "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z")
    val Link = icon(
        "Link",
        "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z"
    )
    val OpenInBrowser = icon("OpenInBrowser", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
    val Download = icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
    val Stats = icon("Stats", "M5,9.2h3V19H5zM10.6,5h2.8v14h-2.8zM16.2,13H19v6h-2.8z")
    val Quote = icon("Quote", "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM5.92,19H5v-0.92l9.06,-9.06 0.92,0.92L5.92,19zM20.71,5.63l-2.34,-2.34c-0.2,-0.2 -0.45,-0.29 -0.71,-0.29s-0.51,0.1 -0.7,0.29l-1.83,1.83 3.75,3.75 1.83,-1.83c0.39,-0.39 0.39,-1.02 0,-1.41z")
    val AddToList = icon("AddToList", "M14,10H2v2h12v-2zM14,6H2v2h12V6zM18,14v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM2,16h8v-2H2v2z")
    val Qr = icon(
        "Qr",
        "M3,11h8V3H3V11zM5,5h4v4H5V5zM3,21h8v-8H3V21zM5,15h4v4H5V15zM13,3v8h8V3H13zM19,9h-4V5h4V9zM19,19h2v2h-2zM13,13h2v2h-2zM15,15h2v2h-2zM13,17h2v2h-2zM15,19h2v2h-2zM17,17h2v2h-2zM17,13h2v2h-2zM19,15h2v2h-2z"
    )
    val Reply = icon("Reply", "M10,9V5l-7,7 7,7v-4.1c5,0 8.5,1.6 11,5.1 -1,-5 -4,-10 -11,-11z")
    val React = icon(
        "React",
        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM15.5,11c0.83,0 1.5,-0.67 1.5,-1.5S16.33,8 15.5,8 14,8.67 14,9.5s0.67,1.5 1.5,1.5zM8.5,11c0.83,0 1.5,-0.67 1.5,-1.5S9.33,8 8.5,8 7,8.67 7,9.5 7.67,11 8.5,11zM12,17.5c2.33,0 4.31,-1.46 5.11,-3.5H6.89c0.8,2.04 2.78,3.5 5.11,3.5z"
    )
    val MuteThread = icon(
        "MuteThread",
        "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h9.17l-2,-2H6l-2,2V4.83L2.71,3.54 1.29,4.96 19.04,22.71l1.41,-1.41L20,20.85V18c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,16h-0.17L8,4.17V4h12v12z"
    )
    val Person = icon("Person", "M12,6c1.1,0 2,0.9 2,2s-0.9,2 -2,2 -2,-0.9 -2,-2 0.9,-2 2,-2m0,10c2.7,0 5.8,1.29 6,2H6c0.23,-0.72 3.31,-2 6,-2m0,-12C9.79,4 8,5.79 8,8s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z")
    /** Picture-in-picture: a small window inside a big one. */
    val Pip = icon("Pip", "M19,11h-8v6h8v-6zM23,19L23,4.98C23,3.88 22.1,3 21,3L3,3c-1.1,0 -2,0.88 -2,1.98L1,19c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2zM21,19.02L3,19.02L3,4.97h18v14.05z")
    val Bell = icon("Bell", "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32L13.5,4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2zM16,17L8,17v-6c0,-2.48 1.51,-4.5 4,-4.5s4,2.02 4,4.5v6z")
    val Shield = icon("Shield", "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4zM19,11c0,4.52 -2.98,8.69 -7,9.93 -4.02,-1.24 -7,-5.41 -7,-9.93L5,6.3l7,-3.11 7,3.11L19,11z")
    val Eye = icon("Eye", "M12,6c3.79,0 7.17,2.13 8.82,5.5C19.17,14.87 15.79,17 12,17s-7.17,-2.13 -8.82,-5.5C4.83,8.13 8.21,6 12,6m0,-2C7,4 2.73,7.11 1,11.5 2.73,15.89 7,19 12,19s9.27,-3.11 11,-7.5C21.27,7.11 17,4 12,4zM12,9c1.38,0 2.5,1.12 2.5,2.5S13.38,14 12,14s-2.5,-1.12 -2.5,-2.5S10.62,9 12,9m0,-2c-2.48,0 -4.5,2.02 -4.5,4.5S9.52,16 12,16s4.5,-2.02 4.5,-4.5S14.48,7 12,7z")
    val Server = icon("Server", "M20,13H4c-0.55,0 -1,0.45 -1,1v6c0,0.55 0.45,1 1,1h16c0.55,0 1,-0.45 1,-1v-6c0,-0.55 -0.45,-1 -1,-1zM7,19c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM20,3H4c-0.55,0 -1,0.45 -1,1v6c0,0.55 0.45,1 1,1h16c0.55,0 1,-0.45 1,-1V4c0,-0.55 -0.45,-1 -1,-1zM7,9c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z")
    val Clock = icon("Clock", "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z")
    val Info = icon("Info", "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z")
    val Search = icon("Search", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z")
    val Chevron = icon("Chevron", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
    val Close = icon("Close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")

    // Drawn for Block's profile page, in the same line weight as the zap bolt.
    /** A speech bubble: send a direct message. */
    val Chat = stroked("Chat", "M7,4.5h10a3.5,3.5 0,0 1,3.5 3.5v6a3.5,3.5 0,0 1,-3.5 3.5h-5.5l-4.5,3.5v-3.5a3.5,3.5 0,0 1,-3.5 -3.5v-6a3.5,3.5 0,0 1,3.5 -3.5z")
    /** Two overlapping rounded sheets: copy. */
    val CopyLine = stroked(
        "CopyLine",
        "M11,8.5h7a2.5,2.5 0,0 1,2.5 2.5v7a2.5,2.5 0,0 1,-2.5 2.5h-7a2.5,2.5 0,0 1,-2.5 -2.5v-7a2.5,2.5 0,0 1,2.5 -2.5zM15.5,8.5V6a2.5,2.5 0,0 0,-2.5 -2.5H6A2.5,2.5 0,0 0,3.5 6v7a2.5,2.5 0,0 0,2.5 2.5h2.5",
        2f
    )
    /** Three dots in a row: more options. */
    val MoreHoriz = icon(
        "MoreHoriz",
        "M6,10.2a1.8,1.8 0,1 0,0.001 0zM12,10.2a1.8,1.8 0,1 0,0.001 0zM18,10.2a1.8,1.8 0,1 0,0.001 0z"
    )

    // Feed icons and the feed sheet's buttons, drawn for Block in the same line.
    val ArrowUp = stroked("ArrowUp", "M12,19V5M6,11l6,-6 6,6", 2f)
    val ArrowDown = stroked("ArrowDown", "M12,5V19M6,13l6,6 6,-6", 2f)
    val Plus = stroked("Plus", "M12,5v14M5,12h14", 2f)
    val Check = stroked("Check", "M5,12.5l4.5,4.5L19,7.5", 2.2f)
    val Trash = stroked("Trash", "M5,7h14M10,7V5h4v2M7,7l0.8,12a1.5,1.5 0,0 0,1.5 1.4h5.4a1.5,1.5 0,0 0,1.5 -1.4L17,7M10.5,11v5.5M13.5,11v5.5")
    val Sliders = stroked("Sliders", "M4,7h9M17,7h3M4,17h3M11,17h9M15,5v4M9,15v4", 1.9f)
    val Following = stroked("Following", "M12,4a3.8,3.8 0,1 0,0.001 0zM4.5,20c0.8,-3.6 3.8,-5.5 7.5,-5.5s6.7,1.9 7.5,5.5")
    val Globe = stroked("Globe", "M12,3.5a8.5,8.5 0,1 0,0.001 0zM3.5,12h17M12,3.5c2.6,2.4 3.8,5.2 3.8,8.5s-1.2,6.1 -3.8,8.5M12,3.5c-2.6,2.4 -3.8,5.2 -3.8,8.5s1.2,6.1 3.8,8.5")
    val Hashtag = stroked("Hashtag", "M9.5,4L7.5,20M16.5,4L14.5,20M4.5,9h16M3.5,15h16")
    val Relay = stroked("Relay", "M5.5,4.5h13a1,1 0,0 1,1 1v4a1,1 0,0 1,-1 1h-13a1,1 0,0 1,-1 -1v-4a1,1 0,0 1,1 -1zM5.5,13.5h13a1,1 0,0 1,1 1v4a1,1 0,0 1,-1 1h-13a1,1 0,0 1,-1 -1v-4a1,1 0,0 1,1 -1zM8,7.5h0.01M8,16.5h0.01", 1.8f)
    val Sparkle = stroked("Sparkle", "M11,3.5l1.9,5.1 5.1,1.9 -5.1,1.9L11,17.5l-1.9,-5.1L4,10.5l5.1,-1.9zM18.5,15v5M16,17.5h5")
    val PlayLine = stroked("PlayLine", "M8,5.5v13l10.5,-6.5z")
    val PhotoLine = stroked("PhotoLine", "M5.5,4.5h13a1,1 0,0 1,1 1v13a1,1 0,0 1,-1 1h-13a1,1 0,0 1,-1 -1v-13a1,1 0,0 1,1 -1zM4.5,16l4.5,-4.5 3.5,3.5 2.5,-2.5 4.5,4.5M15.5,8.5h0.01")
    val ListLine = stroked("ListLine", "M9,7h11M9,12h11M9,17h11M4.5,7h0.01M4.5,12h0.01M4.5,17h0.01")

    // The composer's toolbar, drawn for Block.
    val Poll = stroked("Poll", "M5,19.5V13M10,19.5V5M15,19.5V9.5M20,19.5V15.5M3.5,19.5h18")
    val Calendar = stroked("Calendar", "M5.5,5.5h13a1.5,1.5 0,0 1,1.5 1.5v11.5a1.5,1.5 0,0 1,-1.5 1.5h-13a1.5,1.5 0,0 1,-1.5 -1.5V7a1.5,1.5 0,0 1,1.5 -1.5zM4,10h16M8.5,3.5v3.5M15.5,3.5v3.5M12,13v2.5l1.8,1.2")
    val Warning = stroked("Warning", "M12,4.5L21,19.5H3zM12,10v4.5M12,17h0.01")
    val CloseLine = stroked("CloseLine", "M6,6l12,12M18,6L6,18", 2f)
}
