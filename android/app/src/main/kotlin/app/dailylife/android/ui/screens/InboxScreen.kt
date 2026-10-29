package app.dailylife.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dailylife.android.AppViewModel
import app.dailylife.android.Screen
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.itemTitle
import app.dailylife.android.ui.Btn
import app.dailylife.android.ui.C
import app.dailylife.android.ui.Chip
import app.dailylife.android.ui.Empty
import app.dailylife.android.ui.PageHeading
import app.dailylife.android.ui.R12
import app.dailylife.android.ui.ScreenColumn
import app.dailylife.android.ui.T
import app.dailylife.android.ui.card
import app.dailylife.android.ui.kindIcon
import app.dailylife.android.ui.kindKey
import app.dailylife.android.ui.num
import app.dailylife.android.ui.relative
import app.dailylife.android.ui.sourceLabel
import app.dailylife.android.ui.tr
import app.dailylife.core.inbox.Triage
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.UploadState

@Composable
fun InboxScreen(vm: AppViewModel, s: Snapshot) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val items = Triage.queue(s.items).reversed() // newest first in the list; triage walks oldest first
    val media = setOf(CaptureKind.AUDIO, CaptureKind.IMAGE, CaptureKind.VIDEO, CaptureKind.FILE)
    val groups = mapOf(
        "all" to items,
        "notes" to items.filter { it.kind == CaptureKind.TEXT || it.kind == CaptureKind.LIST },
        "links" to items.filter { it.kind == CaptureKind.LINK },
        "media" to items.filter { it.kind in media },
    )
    val pendingUploads = items.flatMap { it.attachments }.count { it.uploadState != UploadState.UPLOADED }

    ScreenColumn {
        PageHeading(tr("inbox"), tr("inboxSubtitle", items.size)) { vm.go(Screen.Search) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { (key, list) -> Chip("${tr(key)} ${num(list.size)}", filter == key, { filter = key }) }
        }
        if (items.isNotEmpty()) Btn(tr("processInbox"), { vm.go(Screen.Process()) })
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            groups.getValue(filter).forEach { InboxCard(it, s.prefs.lang) { vm.go(Screen.Process(it.id)) } }
            if (items.isEmpty()) Empty(Icons.Outlined.Inbox, tr("inboxEmpty"), tr("inboxEmptyHint"))
        }
        // Honest sync state: everything is local until the backend slice ships (SYN01).
        Row(Modifier.fillMaxWidth().clip(R12).background(C.subtle).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (pendingUploads > 0) Icons.Outlined.CloudOff else Icons.Outlined.PhoneAndroid, null, tint = C.secondary, modifier = Modifier.size(20.dp))
            Column {
                Text(tr("localOnly"), style = T.micro)
                Text(tr("savedOnDevice"), style = T.micro)
            }
        }
    }
}

@Composable
fun InboxCard(item: CaptureItem, lang: String, onClick: () -> Unit) {
    val origin = sourceLabel(item.source.service)
    val subtitle = (if (origin != null) tr("sharedFrom", origin) else tr(kindKey(item.kind))) + " · " + relative(item.capturedAt, lang)
    val detail = when {
        item.kind == CaptureKind.LIST -> item.entries.joinToString(" · ") { it.text }
        item.kind == CaptureKind.LINK -> item.url.orEmpty()
        item.attachments.isNotEmpty() && item.body.isBlank() -> tr("attachments", item.attachments.size)
        else -> item.body.lineSequence().firstOrNull().orEmpty()
    }
    Row(Modifier.fillMaxWidth().card().clickable(onClick = onClick).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(R12).background(C.tint), contentAlignment = Alignment.Center) {
            Icon(kindIcon(item.kind), null, tint = C.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(itemTitle(item, lang), style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = T.micro)
            if (detail.isNotBlank()) Text(detail, style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
