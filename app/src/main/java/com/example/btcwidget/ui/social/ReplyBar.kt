package com.example.btcwidget.ui.social

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.ui.main.EInk

/**
 * X's "Post your reply" bar: your avatar, a one-line field that grows while typing, a
 * photo button and ⤢ to open the full composer. Shared by the post screen (pinned at the
 * bottom) and the image viewer. [dark] uses the viewer's colours over the photo.
 */
@Composable
fun ReplyBar(
    vm: SocialViewModel,
    target: Event,
    onLogin: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    onSent: () -> Unit = {}
) {
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val focusManager = LocalFocusManager.current
    var text by remember(target.id) { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var images by remember(target.id) { mutableStateOf<List<SocialViewModel.Attachment>>(emptyList()) }
    var uploading by remember { mutableStateOf(0) }
    var sending by remember { mutableStateOf(false) }

    val fill = if (dark) Color(0xFF2A2F36) else EInk.Body
    val ink = if (dark) Color(0xFFEDEFF1) else EInk.Value
    val hint = if (dark) Color(0xFF9AA0A6) else EInk.Caption

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploading++
            vm.uploadImage(uri) { a -> uploading--; if (a != null) images = images + a }
        }
    }
    val me = account?.pubkey
    val canSend = !sending && uploading == 0 && (text.isNotBlank() || images.isNotEmpty())

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(fill)
            .border(0.5.dp, if (dark) Color.Transparent else EInk.BodyStroke, RoundedCornerShape(26.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        if (images.isNotEmpty() || uploading > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 44.dp, top = 4.dp, bottom = 6.dp).horizontalScroll(rememberScrollState())
            ) {
                images.forEach { img ->
                    Box {
                        AsyncImage(img.url, "Attached image", contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                        Text(
                            "✕", color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color(0x99000000))
                                .clickable(onClickLabel = "Remove image") { images = images - img }.padding(horizontal = 5.dp)
                        )
                    }
                }
                repeat(uploading) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (me != null) Avatar(profiles[me], me, size = 36) else Box(Modifier.size(36.dp).clip(CircleShape).background(hint))
            Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                if (text.isEmpty()) Text("Post your reply", color = hint, fontSize = 16.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = me != null,
                    textStyle = TextStyle(color = ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(ink),
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 22.dp)
                        .onFocusChanged { focused = it.isFocused }
                )
                // Logged out: tapping the field goes to log in instead.
                if (me == null) Box(Modifier.matchParentSize().clickable(onClickLabel = "Log in to reply", onClick = onLogin))
            }
            if (focused || text.isNotEmpty() || images.isNotEmpty()) {
                IconCell(Glyphs.Photo, "Add photo", ink, enabled = images.size + uploading < 4) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Text(
                    if (sending) "…" else "Reply",
                    color = if (canSend) (if (dark) Color.Black else EInk.Background) else hint,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (canSend) ink else Color.Transparent)
                        .clickable(enabled = canSend, onClickLabel = "Send reply") {
                            sending = true
                            vm.send(text.trim(), target, null, images) { ok ->
                                sending = false
                                if (ok) {
                                    text = ""; images = emptyList(); focusManager.clearFocus(); onSent()
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            } else {
                IconCell(Glyphs.Photo, "Add photo", ink, enabled = me != null) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                IconCell(Glyphs.Expand, "Open full reply editor", ink) { if (me == null) onLogin() else onExpand() }
            }
        }
    }
}

@Composable
private fun IconCell(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
    ) { Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(24.dp)) }
}
