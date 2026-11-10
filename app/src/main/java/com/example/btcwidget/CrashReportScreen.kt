package com.example.btcwidget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.EInkChip

/** Shown on the launch after a crash: the saved report, to copy or share, then the app. */
@Composable
fun CrashReportScreen(report: String, onContinue: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(EInk.Background)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Text("Blockface closed unexpectedly", color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "This report stays on your phone. Copy or share it to the developer to help fix the problem.",
            color = EInk.Label, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            EInkChip(text = "COPY", selected = true, onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Blockface crash report", report))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            })
            EInkChip(text = "SHARE", selected = false, onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                context.startActivity(Intent.createChooser(send, "Share crash report"))
            })
            EInkChip(text = "CONTINUE", selected = false, onClick = onContinue)
        }
        SelectionContainer(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(EInk.Body)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            Text(report, color = EInk.Value, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
