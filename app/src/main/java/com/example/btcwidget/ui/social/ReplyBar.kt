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
import androidx.compose.ui.draw.shadow
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
 * X's floating "Post your reply" card. Collapsed: your avatar, "Post your reply" and ⤢ to
 * open the full composer. Tapped: the field grows and a toolbar (photo, Reply) appears, the
 * card riding above the keyboard over the content. Shared by the post screen and the image
 * viewer; [dark] uses the viewer's colours. [onActiveChange] says when it is expanded.
 */
@Composable
fun ReplyBar(
    vm: SocialViewModel,
    target: Event,
    onLogin: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    onSent: () -> Unit = {},
    onActiveChange: (Boolean) -> Unit = {}
) {
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val focusManager = LocalFocusManager.current
    var text by remember(target.id) { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var images by remember(target.id) { mutableStateOf<List<SocialViewModel.Attachment>>(emptyList()) }
    var uploading by remember { mutableStateOf(0) }
    var sending by remember { mutableStateOf(false) }

    // Slightly see-through, like X's card over the post.
    val fill = if (dark) Color(0xF02A2F36) else EInk.Body.copy(alpha = 0.97f)
    val ink = if (dark) Color(0xFFEDEFF1) else EInk.Value
    val hint = if (dark) Color(0xFF9AA0A6) else EInk.Caption
    val shape = RoundedCornerShape(22.dp)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploading++
            vm.uploadImage(uri) { a -> uploading--; if (a != null) images = images + a }
        }
    }
    val me = account?.pubkey
    val active = focused || text.isNotEmpty() || images.isNotEmpty() || uploading > 0
    androidx.compose.runtime.LaunchedEffect(active) { onActiveChange(active) }
    val canSend = !sending && uploading == 0 && (text.isNotBlank() || images.isNotEmpty())

    Column(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, shape)
            .clip(shape)
            .background(fill)
            .border(0.5.dp, if (dark) Color(0x33FFFFFF) else EInk.BodyStroke, shape)
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = if (active) Alignment.Top else Alignment.CenterVertically) {
            Box(Modifier.padding(top = if (active) 4.dp else 0.dp)) {
                if (me != null) Avatar(profiles[me], me, size = 32) else Box(Modifier.size(32.dp).clip(CircleShape).background(hint))
            }
            Box(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp, top = if (active) 9.dp else 0.dp)) {
                if (text.isEmpty()) Text("Post your reply", color = hint, fontSize = 16.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = me != null,
                    textStyle = TextStyle(color = ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(ink),
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (active) 44.dp else 22.dp)
                        .onFocusChanged { focused = it.isFocused }
                )
                // Logged out: tapping the field goes to log in instead.
                if (me == null) Box(Modifier.matchParentSize().clickable(onClickLabel = "Log in to reply", onClick = onLogin))
            }
            IconCell(Glyphs.Expand, "Open full reply editor", ink, iconSize = 20) { if (me == null) onLogin() else onExpand() }
        }
        if (images.isNotEmpty() || uploading > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 42.dp, top = 6.dp, bottom = 2.dp).horizontalScroll(rememberScrollState())
            ) {
                images.forEach { img ->
                    Box {
                        AsyncImage(img.url, "Attached image", contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                        Text(
                            "✕", color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color(0x99000000))
                                .clickable(onClickLabel = "Remove image") { images = images - img }.padding(horizontal = 5.dp)
                        )
                    }
                }
                repeat(uploading) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (active) {
            // X's toolbar: media on the left, Reply on the right.
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconCell(Glyphs.Photo, "Add photo", ink, enabled = images.size + uploading < 4) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Box(Modifier.weight(1f))
                Text(
                    if (sending) "Replying…" else "Reply",
                    color = if (canSend) Color.Black else hint,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(50))
                        // Block's accent where X uses its blue.
                        .background(if (canSend) Color(0xFFF7931A) else hint.copy(alpha = 0.25f))
                        .clickable(enabled = canSend, onClickLabel = "Send reply") {
                            sending = true
                            vm.send(text.trim(), target, null, images) { ok ->
                                sending = false
                                if (ok) {
                                    text = ""; images = emptyList(); focusManager.clearFocus(); onSent()
                                }
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun IconCell(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, enabled: Boolean = true, iconSize: Int = 24, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
    ) { Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(iconSize.dp)) }
}
