package app.dailylife.android.ui.process

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
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
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Checkbox
import app.dailylife.android.ui.components.Chip
import app.dailylife.android.ui.components.Corner16
import app.dailylife.android.ui.components.Field
import app.dailylife.android.ui.components.MenuRow
import app.dailylife.android.ui.components.Segmented
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.components.pickDate
import app.dailylife.android.ui.format.shortDate
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.WaitingFor
import java.time.LocalDate

@Composable
internal fun Decisions(
    vm: AppViewModel,
    item: CaptureItem,
    panel: Panel,
    setPanel: (Panel) -> Unit,
) {
    Text(tr("whatBecome"), style = AppType.h3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton(tr("createTask"), { vm.inbox.toTask(item) })
        AppButton(tr("doNow"), { vm.inbox.startDoNow(item) }, style = ButtonStyle.SECONDARY)
        AppButton(
            tr("saveReference"),
            { vm.inbox.toReference(item) },
            style = ButtonStyle.SECONDARY,
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                setPanel(if (panel == Panel.NONE) Panel.MENU else Panel.NONE)
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("otherDecisions"), style = AppType.small)
            Icon(Icons.Outlined.MoreHoriz, null, tint = AppColors.secondary)
        }
        when (panel) {
            Panel.MENU -> OtherDecisionsMenu(vm, item, setPanel)
            Panel.SOMEDAY -> SomedayPanel(vm) { reviewOn -> vm.inbox.toSomeday(item, reviewOn) }
            Panel.WAITING -> WaitingPanel(vm) { waiting -> vm.inbox.toWaiting(item, waiting) }
            Panel.SPLIT -> SplitPanel(item) { parts -> vm.inbox.split(item, parts) }
            Panel.NONE -> Unit
        }
    }
}

@Composable
private fun OtherDecisionsMenu(vm: AppViewModel, item: CaptureItem, setPanel: (Panel) -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(4.dp)) {
        MenuRow(tr("someday")) { setPanel(Panel.SOMEDAY) }
        MenuRow(tr("waitingFor")) { setPanel(Panel.WAITING) }
        MenuRow(tr("split")) { setPanel(Panel.SPLIT) }
        if (item.kind == CaptureKind.TEXT)
            MenuRow(tr("convertToList")) { vm.inbox.convertToList(item) }
        MenuRow(tr("moveToTrash"), danger = true) { vm.inbox.trash(item) }
    }
}

/** Someday with an optional review date (TRI05). */
@Composable
private fun SomedayPanel(vm: AppViewModel, onConfirm: (LocalDate?) -> Unit) {
    val context = LocalContext.current
    var reviewOn by remember { mutableStateOf<LocalDate?>(null) }
    PanelCard {
        val label = "${tr("reviewOn")}: ${reviewOn?.let { shortDate(it, vm.lang) } ?: tr("notSet")}"
        AppButton(
            label,
            { pickDate(context, vm.today().plusWeeks(4), vm.today()) { reviewOn = it } },
            style = ButtonStyle.SECONDARY,
        )
        AppButton(tr("someday"), { onConfirm(reviewOn) })
    }
}

/** Who the user is waiting for and when to follow up; nobody is messaged (TRI06). */
@Composable
private fun WaitingPanel(vm: AppViewModel, onConfirm: (WaitingFor) -> Unit) {
    val context = LocalContext.current
    var who by remember { mutableStateOf("") }
    var followUpOn by remember { mutableStateOf(vm.today().plusDays(3)) }
    PanelCard {
        Field(who, tr("who")) { who = it }
        AppButton(
            "${tr("followUpOn")}: ${shortDate(followUpOn, vm.lang)}",
            { pickDate(context, followUpOn, vm.today()) { followUpOn = it } },
            style = ButtonStyle.SECONDARY,
        )
        AppButton(
            tr("waitingFor"),
            { onConfirm(WaitingFor(who.trim(), null, vm.today(), followUpOn)) },
            enabled = who.isNotBlank(),
        )
    }
}

/** One part per line; each becomes its own inbox item (TRI07). */
@Composable
private fun SplitPanel(item: CaptureItem, onConfirm: (List<String>) -> Unit) {
    var text by remember {
        mutableStateOf(listOf(item.title, item.body).filter { it.isNotBlank() }.joinToString("\n"))
    }
    PanelCard {
        Text(tr("splitHint"), style = AppType.micro)
        Field(text, "", minHeight = 120) { text = it }
        val parts = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        AppButton(tr("split") + " (${parts.size})", { onConfirm(parts) }, enabled = parts.size >= 2)
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Do Now: work happens outside the app; completion must be confirmed (TRI02, AC04). */
@Composable
internal fun DoingNow(vm: AppViewModel, item: CaptureItem) {
    Column(
        Modifier.fillMaxWidth().clip(Corner16).background(AppColors.tint).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("doNowRunning"), style = AppType.h3, color = AppColors.primary)
        Text(tr("doNowHint"), style = AppType.small)
    }
    AppButton(tr("markDone"), { vm.inbox.confirmDoNow(item) })
    AppButton(tr("doNowToTask"), { vm.inbox.toTask(item) }, style = ButtonStyle.SECONDARY)
}

/** Lists are processed entry by entry; unselected entries stay in the inbox (CAP04, AC03). */
@Composable
internal fun EntriesPanel(vm: AppViewModel, item: CaptureItem) {
    val choice =
        remember(item.id) { mutableStateMapOf<String, String>() } // entryId -> "task" | "ref"
    var asSubtasks by remember(item.id) { mutableStateOf(false) }
    Text(tr("processEntries"), style = AppType.h3)
    Column(Modifier.fillMaxWidth().card().padding(8.dp)) {
        item.entries
            .filter { it.resolvedAs == null }
            .forEach { e ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.weight(1f).clickable {
                            if (choice.containsKey(e.id)) choice.remove(e.id)
                            else choice[e.id] = "task"
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(choice.containsKey(e.id))
                        Text(e.text, style = AppType.body)
                    }
                    if (choice.containsKey(e.id))
                        Segmented(
                            listOf("task" to tr("toTasks"), "ref" to tr("toReference")),
                            choice.getValue(e.id),
                            { choice[e.id] = it },
                            Modifier.size(width = 170.dp, height = 44.dp),
                        )
                }
            }
    }
    // Several tasks can stay together as subtasks of one task named after the item (TRI03).
    if (choice.values.count { it == "task" } > 1)
        Chip(tr("asSubtasks"), asSubtasks, { asSubtasks = !asSubtasks })
    AppButton(
        tr("applyEntries"),
        {
            vm.inbox.resolveEntries(
                item,
                choice.filterValues { it == "task" }.keys.toList(),
                choice.filterValues { it == "ref" }.keys.toList(),
                asSubtasks = asSubtasks,
            )
            choice.clear()
        },
        enabled = choice.isNotEmpty(),
    )
    AppButton(tr("moveToTrash"), { vm.inbox.trash(item) }, style = ButtonStyle.QUIET)
}
