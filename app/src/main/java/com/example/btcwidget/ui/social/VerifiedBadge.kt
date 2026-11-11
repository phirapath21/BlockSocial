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
 * The verified mark (NIP-05 address confirmed by its domain), shaped like X's: a scalloped
 * badge in the app's orange with a white tick.
 */
private val VerifiedMark: ImageVector by lazy {
    ImageVector.Builder(name = "Verified", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes(
                "M23,12l-2.44,-2.79l0.34,-3.69l-3.61,-0.82L15.4,1.5L12,2.96L8.6,1.5L6.71,4.69L3.1,5.5L3.44,9.2L1,12" +
                    "l2.44,2.79l-0.34,3.7l3.61,0.82L8.6,22.5l3.4,-1.47l3.4,1.46l1.89,-3.19l3.61,-0.82l-0.34,-3.69L23,12z"
            ),
            fill = SolidColor(Color(0xFFF7931A))
        )
        .addPath(
            pathData = addPathNodes("M7.6,12.3l3,3l5.8,-6"),
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        .build()
}

@Composable
fun VerifiedBadge(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Image(
        VerifiedMark, contentDescription = null,
        modifier = modifier.size(size).semantics { contentDescription = "Verified" }
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
