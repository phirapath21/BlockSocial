package com.example.btcwidget.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.btcwidget.nostr.core.Profile

/**
 * The verified mark (NIP-05 address confirmed by its domain): a rounded square in the app's
 * orange with a white tick.
 */
private val VerifiedMark: ImageVector by lazy {
    ImageVector.Builder(name = "Verified", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            // A rounded square (5.5 of 24 corners), the app's orange.
            pathData = addPathNodes("M8,2.5h8a5.5,5.5 0 0,1 5.5,5.5v8a5.5,5.5 0 0,1 -5.5,5.5h-8a5.5,5.5 0 0,1 -5.5,-5.5v-8a5.5,5.5 0 0,1 5.5,-5.5z"),
            fill = SolidColor(Color(0xFFF7931A))
        )
        .addPath(
            pathData = addPathNodes("M7.6,12.3l3,3l5.8,-6"),
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        .build()
}

@Composable
fun VerifiedBadge(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Image(
        VerifiedMark, contentDescription = null,
        modifier = modifier.size(size).semantics { contentDescription = tr("Verified") }
    )
}

/**
 * Someone's name in bold with the verified badge after it when their NIP-05 address checks out.
 * A long name ends in "…" and the badge stays visible.
 */
@Composable
fun DisplayName(
    profile: Profile?, pubkey: String, color: Color, fontSize: TextUnit,
    modifier: Modifier = Modifier, name: String = profile?.bestName ?: Profile.shortNpub(pubkey)
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            name, color = color, fontSize = fontSize, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
        )
        if (isVerified(profile, pubkey)) VerifiedBadge((fontSize.value * 1.05f).dp, Modifier.padding(start = 3.dp))
    }
}
