package com.example.btcwidget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.social.BlockLogo
import com.example.btcwidget.ui.social.Glyphs
import com.example.btcwidget.ui.social.tr

private val Accent = Color(0xFFF7931A)

/**
 * Shown on the launch after a crash: what happened, a box to say what you were doing, and
 * Report on GitHub (a new issue opens in the browser, filled in, for you to check and submit),
 * Copy and Share; then Continue to the app. Nothing is sent without you.
 */
@Composable
fun CrashReportScreen(report: String, onContinue: () -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(EInk.Background).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        BlockLogo(Modifier.size(56.dp))
        Text(tr("Sorry, Block closed unexpectedly"), color = EInk.Value, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(
            tr("A report of what went wrong was saved on your phone. Sending it helps fix the problem: it opens a new issue on GitHub, filled in, for you to check and submit."),
            color = EInk.Label, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp)
        )
        // What you were doing: the most useful part of a report.
        Box(
            Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 88.dp).clip(RoundedCornerShape(14.dp))
                .background(EInk.Body).padding(12.dp)
        ) {
            BasicTextField(
                value = note, onValueChange = { note = it.take(500) },
                textStyle = TextStyle(color = EInk.Value, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { field ->
                    Box {
                        if (note.isEmpty()) Text(tr("What were you doing when it happened? (optional)"), color = EInk.Caption, fontSize = 15.sp)
                        field()
                    }
                }
            )
        }
        CrashButton(Glyphs.OpenInBrowser, tr("Report on GitHub"), primary = true) {
            val url = CrashIssue.url(report, note)
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { Toast.makeText(context, tr("No browser to open GitHub"), Toast.LENGTH_SHORT).show() }
        }
        Text(
            tr("Needs a GitHub account. Issues are public: the report holds your phone model, Android version and the error, nothing else."),
            color = EInk.Caption, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            CrashButton(Glyphs.CopyLine, tr("Copy"), modifier = Modifier.weight(1f)) {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Block crash report", (note.trim().takeIf { it.isNotEmpty() }?.let { "$it\n\n" } ?: "") + report))
                Toast.makeText(context, tr("Copied"), Toast.LENGTH_SHORT).show()
            }
            CrashButton(Glyphs.Share, tr("Share"), modifier = Modifier.weight(1f)) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, (note.trim().takeIf { it.isNotEmpty() }?.let { "$it\n\n" } ?: "") + report)
                context.startActivity(Intent.createChooser(send, tr("Share crash report")))
            }
        }
        CrashButton(null, tr("Continue to Block"), modifier = Modifier.padding(top = 10.dp), onClick = onContinue)
        // The report itself, folded away.
        Text(
            if (details) tr("Hide the report") else tr("Show the report"), color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp).clip(RoundedCornerShape(50)).clickable { details = !details }.padding(vertical = 6.dp)
        )
        if (details) SelectionContainer(
            Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)).background(EInk.Body)
                .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(10.dp)
        ) {
            Text(report, color = EInk.Value, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/** A full-width round button: orange for the main action, outlined for the rest. */
@Composable
private fun CrashButton(icon: ImageVector?, label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(top = if (primary) 16.dp else 0.dp).clip(RoundedCornerShape(50))
            .then(if (primary) Modifier.background(Accent) else Modifier.border(1.dp, EInk.BodyStroke, RoundedCornerShape(50)))
            .clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        val ink = if (primary) Color.Black else EInk.Value
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.padding(end = 8.dp).size(18.dp))
        Text(label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
