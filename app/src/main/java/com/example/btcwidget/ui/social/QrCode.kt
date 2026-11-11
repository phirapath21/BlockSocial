package com.example.btcwidget.ui.social

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.ArticleKey
import com.example.btcwidget.HashtagKey
import com.example.btcwidget.NoteKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/** [text] as a QR code: dark squares on white, with a quiet border so cameras read it. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
    }
    Canvas(modifier.background(Color.White)) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** Someone's profile as a QR code (nostr:npub…), to show to a friend, with copy, share and scan. */
@Composable
fun ProfileQrDialog(vm: SocialViewModel, pubkey: String, navigate: (NavKey) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val profile = vm.profiles.value[pubkey]
    val npub = remember(pubkey) { Nip19.npub(pubkey) }
    val scan = rememberQrScan(vm, navigate, onOpened = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile?.bestName ?: Profile.shortNpub(pubkey), maxLines = 1) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                QrCode("nostr:$npub", Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)))
                Text(
                    npub.take(16) + "…" + npub.takeLast(8), fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)
                )
                Text("Scan with any Nostr app to follow.", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { copyText(context, npub) }) { Text("Copy npub") }
                    TextButton(onClick = { shareText(context, "nostr:$npub") }) { Text("Share") }
                }
            }
        },
        confirmButton = { TextButton(onClick = scan) { Text("Scan a code") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/**
 * Returns an action that scans a QR code and opens what it points to: a profile (npub,
 * nprofile, name@domain), a note, an article or a #tag. Anything else shows a message.
 */
@Composable
fun rememberQrScan(vm: SocialViewModel, navigate: (NavKey) -> Unit, onOpened: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        QrScanner.scan(context) { raw ->
            val text = raw?.trim()?.removePrefix("nostr:")?.removePrefix("NOSTR:") ?: return@scan
            scope.launch {
                val key: NavKey? = when (val target = vm.resolve(text)) {
                    is NostrRepository.Direct.ProfileTarget -> ProfileKey(target.pubkey)
                    is NostrRepository.Direct.NoteTarget -> NoteKey(target.id)
                    is NostrRepository.Direct.HashtagTarget -> HashtagKey(target.tag)
                    is NostrRepository.Direct.ArticleTarget -> target.address.let { ArticleKey(Nip19.naddr(it.kind, it.pubkey, it.identifier, it.relays)) }
                    null -> null
                }
                if (key == null) Toast.makeText(context, "That QR code isn't a Nostr profile or post", Toast.LENGTH_SHORT).show()
                else { onOpened(); navigate(key) }
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("npub", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share profile").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** A small "QR" button. */
@Composable
internal fun QrButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = label, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) { Text("▣ QR", color = com.example.btcwidget.ui.main.EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}
