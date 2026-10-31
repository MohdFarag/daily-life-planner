package app.dailylife.android.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dailylife.android.data.Snapshot
import app.dailylife.android.navigation.Screen
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.Chip
import app.dailylife.android.ui.components.Corner12
import app.dailylife.android.ui.components.Empty
import app.dailylife.android.ui.components.PageHeading
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.inbox.Triage
import app.dailylife.core.model.CaptureKind

@Composable
fun InboxScreen(vm: AppViewModel, s: Snapshot) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val items =
        Triage.queue(s.items).reversed() // newest first in the list; triage walks oldest first
    val media = setOf(CaptureKind.AUDIO, CaptureKind.IMAGE, CaptureKind.VIDEO, CaptureKind.FILE)
    val groups =
        mapOf(
            "all" to items,
            "notes" to items.filter { it.kind == CaptureKind.TEXT || it.kind == CaptureKind.LIST },
            "links" to items.filter { it.kind == CaptureKind.LINK },
            "media" to items.filter { it.kind in media },
        )
    val signedIn = vm.account.account.collectAsStateWithLifecycle().value != null
    val summary = inboxSyncSummary(items, s.outbox, signedIn)

    ScreenColumn {
        PageHeading(tr("inbox"), tr("inboxSubtitle", items.size)) {
            vm.navigator.open(Screen.Search)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { (key, list) ->
                Chip("${tr(key)} ${num(list.size)}", filter == key, { filter = key })
            }
        }
        if (items.isNotEmpty())
            AppButton(tr("processInbox"), { vm.navigator.open(Screen.Process()) })
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            groups.getValue(filter).forEach {
                InboxCard(it, s.lang, syncBadge(it, s.outbox, signedIn)) {
                    vm.navigator.open(Screen.Process(it.id))
                }
            }
            if (items.isEmpty()) Empty(Icons.Outlined.Inbox, tr("inboxEmpty"), tr("inboxEmptyHint"))
        }
        SyncFooter(summary) { vm.navigator.open(Screen.Account) }
    }
}

/** Honest sync state for the inbox (SYN01); tapping opens the account screen. */
@Composable
private fun SyncFooter(summary: InboxSyncSummary, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(Corner12)
            .background(AppColors.subtle)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SyncIcon(summary.badge, Modifier.size(20.dp))
        Column {
            Text(
                if (summary.waiting > 0) tr("waitingToSync", summary.waiting)
                else tr(syncKey(summary.badge)),
                style = AppType.micro,
            )
            Text(tr("savedOnDevice"), style = AppType.micro)
        }
    }
}
