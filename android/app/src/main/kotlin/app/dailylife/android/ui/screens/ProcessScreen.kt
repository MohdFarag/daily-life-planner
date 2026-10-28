package app.dailylife.android.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.dailylife.android.AppViewModel
import app.dailylife.android.Screen
import app.dailylife.android.Tab
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.Badge
import app.dailylife.android.ui.Btn
import app.dailylife.android.ui.BtnStyle
import app.dailylife.android.ui.C
import app.dailylife.android.ui.Checkbox
import app.dailylife.android.ui.Empty
import app.dailylife.android.ui.IconBtn
import app.dailylife.android.ui.R12
import app.dailylife.android.ui.R16
import app.dailylife.android.ui.R24
import app.dailylife.android.ui.ScreenColumn
import app.dailylife.android.ui.Segmented
import app.dailylife.android.ui.T
import app.dailylife.android.ui.TopBar
import app.dailylife.android.ui.card
import app.dailylife.android.ui.kindIcon
import app.dailylife.android.ui.kindKey
import app.dailylife.android.ui.pickDate
import app.dailylife.android.ui.relative
import app.dailylife.android.ui.shortDate
import app.dailylife.android.ui.tr
import app.dailylife.core.inbox.Triage
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.CaptureState
import app.dailylife.core.model.WaitingFor
import java.io.File
import java.time.LocalDate

private enum class Panel { NONE, MENU, WAITING, SOMEDAY, SPLIT }

@Composable
fun ProcessScreen(vm: AppViewModel, s: Snapshot, itemId: String?) {
    val queue = Triage.queue(s.items)
    val item = queue.firstOrNull { it.id == itemId } ?: queue.firstOrNull()
    var panel by remember(item?.id) { mutableStateOf(Panel.NONE) }

    ScreenColumn(gap = 20.dp, bottomPadding = 32.dp) {
        TopBar(tr("inbox"), { vm.back() }) { IconBtn(Icons.Outlined.Close, tr("close"), { vm.tab(Tab.INBOX) }) }
        if (item == null) {
            Empty(Icons.Outlined.DoneAll, tr("inboxZero"), tr("inboxZeroHint"))
            Btn(tr("backToInbox"), { vm.tab(Tab.INBOX) })
            return@ScreenColumn
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(tr("itemOf", queue.indexOf(item) + 1, queue.size), style = T.micro)
            Row(
                Modifier.clip(R12).clickable(enabled = vm.canUndo) { vm.undoLast() }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = if (vm.canUndo) C.secondary else C.border, modifier = Modifier.size(18.dp))
                Text(tr("undo"), style = T.label, color = if (vm.canUndo) C.secondary else C.border)
            }
        }

        Preview(item, s.prefs.lang)

        when {
            item.state == CaptureState.DOING_NOW -> DoingNow(vm, item)
            item.kind == CaptureKind.LIST && item.entries.any { it.resolvedAs == null } -> EntriesPanel(vm, item)
            else -> Decisions(vm, item, panel) { panel = it }
        }
    }
}

@Composable
private fun Preview(item: CaptureItem, lang: String) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().card(R24).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Badge(tr(kindKey(item.kind)), C.warning, C.warningText)
        Text(item.title, style = T.h2)
        if (item.body.isNotBlank()) Text(item.body, style = T.body.copy(color = C.secondary))
        item.url?.let { url ->
            Row(Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.OpenInNew, null, tint = C.primary, modifier = Modifier.size(16.dp))
                Text(url, style = T.label, color = C.primary)
            }
        }
        item.attachments.forEach { a ->
            val f = File(a.localPath)
            Text(
                "${f.name} · ${a.sizeBytes / 1024} KB",
                style = T.label, color = C.primary,
                modifier = Modifier.clickable {
                    runCatching {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(kindIcon(item.kind), null, tint = C.secondary, modifier = Modifier.size(18.dp))
            Text(tr("capturedAgo", relative(item.capturedAt, lang)), style = T.micro)
        }
    }
}

@Composable
private fun Decisions(vm: AppViewModel, item: CaptureItem, panel: Panel, setPanel: (Panel) -> Unit) {
    val context = LocalContext.current
    Text(tr("whatBecome"), style = T.h3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(tr("createTask"), { vm.toTask(item) })
        Btn(tr("doNow"), { vm.startDoNow(item) }, style = BtnStyle.SECONDARY)
        Btn(tr("saveReference"), { vm.move(item, CaptureState.REFERENCE) }, style = BtnStyle.SECONDARY)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { setPanel(if (panel == Panel.NONE) Panel.MENU else Panel.NONE) },
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("otherDecisions"), style = T.small)
            Icon(Icons.Outlined.MoreHoriz, null, tint = C.secondary)
        }
        when (panel) {
            Panel.MENU -> Column(Modifier.fillMaxWidth().card().padding(4.dp)) {
                MenuRow(tr("someday")) { setPanel(Panel.SOMEDAY) }
                MenuRow(tr("waitingFor")) { setPanel(Panel.WAITING) }
                MenuRow(tr("split")) { setPanel(Panel.SPLIT) }
                if (item.kind == CaptureKind.TEXT) MenuRow(tr("convertToList")) { vm.convertToList(item) }
                MenuRow(tr("moveToTrash"), danger = true) { vm.move(item, CaptureState.TRASHED) }
            }
            Panel.SOMEDAY -> {
                var review by remember { mutableStateOf<LocalDate?>(null) }
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Btn("${tr("reviewOn")}: ${review?.let { shortDate(it, vm.lang) } ?: tr("notSet")}", { pickDate(context, vm.today().plusWeeks(4), vm.today()) { review = it } }, style = BtnStyle.SECONDARY)
                    Btn(tr("someday"), { vm.move(item, CaptureState.SOMEDAY, reviewOn = review) })
                }
            }
            Panel.WAITING -> {
                var who by remember { mutableStateOf("") }
                var follow by remember { mutableStateOf(vm.today().plusDays(3)) }
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(who, tr("who")) { who = it }
                    Btn("${tr("followUpOn")}: ${shortDate(follow, vm.lang)}", { pickDate(context, follow, vm.today()) { follow = it } }, style = BtnStyle.SECONDARY)
                    Btn(tr("waitingFor"), { vm.move(item, CaptureState.WAITING, WaitingFor(who.trim(), null, vm.today(), follow)) }, enabled = who.isNotBlank())
                }
            }
            Panel.SPLIT -> {
                var parts by remember { mutableStateOf(listOf(item.title, item.body).filter { it.isNotBlank() }.joinToString("\n")) }
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("splitHint"), style = T.micro)
                    Field(parts, "", minHeight = 120) { parts = it }
                    val lines = parts.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    Btn(tr("split") + " (${lines.size})", { vm.split(item, lines) }, enabled = lines.size >= 2)
                }
            }
            Panel.NONE -> Unit
        }
    }
}

/** Do Now: work happens outside the app; completion must be confirmed (TRI02, AC04). */
@Composable
private fun DoingNow(vm: AppViewModel, item: CaptureItem) {
    Column(Modifier.fillMaxWidth().clip(R16).background(C.tint).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("doNowRunning"), style = T.h3, color = C.primary)
        Text(tr("doNowHint"), style = T.small)
    }
    Btn(tr("markDone"), { vm.confirmDoNow(item) })
    Btn(tr("doNowToTask"), { vm.toTask(item) }, style = BtnStyle.SECONDARY)
}

/** Lists are processed entry by entry; unselected entries stay in the inbox (CAP04, AC03). */
@Composable
private fun EntriesPanel(vm: AppViewModel, item: CaptureItem) {
    val choice = remember(item.id) { mutableStateMapOf<String, String>() } // entryId -> "task" | "ref"
    Text(tr("processEntries"), style = T.h3)
    Column(Modifier.fillMaxWidth().card().padding(8.dp)) {
        item.entries.filter { it.resolvedAs == null }.forEach { e ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).clickable { if (choice.containsKey(e.id)) choice.remove(e.id) else choice[e.id] = "task" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(choice.containsKey(e.id))
                    Text(e.text, style = T.body)
                }
                if (choice.containsKey(e.id)) Segmented(
                    listOf("task" to tr("toTasks"), "ref" to tr("toReference")), choice.getValue(e.id), { choice[e.id] = it },
                    Modifier.size(width = 170.dp, height = 44.dp),
                )
            }
        }
    }
    Btn(tr("applyEntries"), {
        vm.resolveEntries(item, choice.filterValues { it == "task" }.keys.toList(), choice.filterValues { it == "ref" }.keys.toList())
        choice.clear()
    }, enabled = choice.isNotEmpty())
    Btn(tr("moveToTrash"), { vm.move(item, CaptureState.TRASHED) }, style = BtnStyle.QUIET)
}

@Composable
fun MenuRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(label, style = T.body.copy(color = if (danger) C.danger else C.text), modifier = Modifier.fillMaxWidth().clip(R12).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp))
}

@Composable
fun Field(value: String, placeholder: String, minHeight: Int = 0, onChange: (String) -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().heightIn(min = maxOf(minHeight, 48).dp).card(R12).padding(12.dp)) {
        if (value.isEmpty()) Text(placeholder, style = T.body.copy(color = C.secondary))
        BasicTextField(value, onChange, textStyle = T.body, modifier = Modifier.fillMaxWidth())
    }
}
