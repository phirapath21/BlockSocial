package com.example.btcwidget.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.btcwidget.R

/**
 * The app's text font, "Block Sans": Inter for Latin (clean and easy to read, close to what X
 * uses) with Noto Sans Thai merged in, so Thai looks the same on every phone instead of
 * whatever the maker ships. See docs/licenses.
 */
val BlockSans = FontFamily(
    Font(R.font.block_sans_regular, FontWeight.Normal),
    Font(R.font.block_sans_medium, FontWeight.Medium),
    Font(R.font.block_sans_bold, FontWeight.Bold),
)

private fun TextStyle.blockSans() = copy(fontFamily = BlockSans)

/** Material's type scale in Block Sans; body text without Material's extra letter spacing, as on X. */
val Typography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.blockSans(), displayMedium = displayMedium.blockSans(), displaySmall = displaySmall.blockSans(),
        headlineLarge = headlineLarge.blockSans(), headlineMedium = headlineMedium.blockSans(), headlineSmall = headlineSmall.blockSans(),
        titleLarge = titleLarge.blockSans(), titleMedium = titleMedium.blockSans(), titleSmall = titleSmall.blockSans(),
        bodyLarge = bodyLarge.blockSans().copy(letterSpacing = 0.sp),
        bodyMedium = bodyMedium.blockSans().copy(letterSpacing = 0.sp),
        bodySmall = bodySmall.blockSans().copy(letterSpacing = 0.sp),
        labelLarge = labelLarge.blockSans(), labelMedium = labelMedium.blockSans(), labelSmall = labelSmall.blockSans(),
    )
}
