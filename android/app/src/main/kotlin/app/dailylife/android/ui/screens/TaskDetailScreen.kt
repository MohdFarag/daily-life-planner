package app.dailylife.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dailylife.android.AppViewModel
import app.dailylife.android.Screen
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.Badge
import app.dailylife.android.ui.Btn
import app.dailylife.android.ui.BtnStyle
import app.dailylife.android.ui.C
import app.dailylife.android.ui.DetailRow
import app.dailylife.android.ui.IconBtn
import app.dailylife.android.ui.ScreenColumn
import app.dailylife.android.ui.T
import app.dailylife.android.ui.TopBar
import app.dailylife.android.ui.card
import app.dailylife.android.ui.kindIcon
import app.dailylife.android.ui.num
import app.dailylife.android.ui.pickDate
import app.dailylife.android.ui.prayerName
import app.dailylife.android.ui.range
import app.dailylife.android.ui.shortDate
import app.dailylife.android.ui.tr
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskStatus
import app.dailylife.core.model.TimingRule

private val ESTIMATES = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

@Composable
fun TaskDetailScreen(vm: AppViewModel, s: Snapshot, id: String, fresh: Boolean) {
    val task = s.tasks.firstOrNull { it.id == id }
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    if (task == null) { androidx.compose.runtime.LaunchedEffect(Unit) { vm.back() }; return }
    val zone = s.prayer.zone
    val sessions = s.sessions.filter { it.taskId == task.id && it.state != SessionState.CANCELED && it.end.isAfter(vm.now()) }.sortedBy { it.start }
    val rules = s.rules.filter { it.taskId == task.id }
    val source = s.items.firstOrNull { it.id == task.sourceItemId }
    val done = task.status == TaskStatus.COMPLETED

    Column(Modifier.fillMaxSize()) {
        ScreenColumn(Modifier.weight(1f), bottomPadding = 16.dp) {
            TopBar(if (fresh) tr("newTask") else tr("task"), { vm.back() }) { IconBtn(Icons.Outlined.MoreHoriz, tr("more"), { menu = !menu }) }
            if (menu) Column(Modifier.fillMaxWidth().card().padding(4.dp)) {
                MenuRow(if (done) tr("reopen") else tr("complete")) { vm.toggleDone(task); menu = false }
                MenuRow(tr("deleteTask"), danger = true) { vm.deleteTask(task) }
            }

            Column {
                BasicTextField(task.title, { vm.saveTask(task.copy(title = it)) }, textStyle = T.h1, modifier = Modifier.fillMaxWidth())
                androidx.compose.foundation.layout.Box {
                    if (task.notes.isEmpty()) Text(tr("notesHint"), style = T.small)
                    BasicTextField(task.notes, { vm.saveTask(task.copy(notes = it)) }, textStyle = T.small, modifier = Modifier.fillMaxWidth())
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.important) Badge("★ " + tr("important"), C.warning, C.warningText) { vm.saveTask(task.copy(important = false)) }
                else Badge(tr("markImportant"), C.subtle, C.secondary) { vm.saveTask(task.copy(important = true)) }
                when (task.status) {
                    TaskStatus.IN_PROGRESS -> Badge(tr("inProgress"), C.tint, C.primary) { vm.saveTask(task.copy(status = TaskStatus.NEEDS_ACTION)) }
                    TaskStatus.COMPLETED -> Badge(tr("completed"), C.tint, C.primary)
                    TaskStatus.NEEDS_ACTION -> Badge(tr("inProgress") + "?", C.subtle, C.secondary) { vm.saveTask(task.copy(status = TaskStatus.IN_PROGRESS)) }
                }
            }

            Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val lists = s.lists
                DetailRow(tr("listLabel"), lists.firstOrNull { it.id == task.listId }?.name ?: "—") {
                    val next = lists[(lists.indexOfFirst { it.id == task.listId } + 1) % lists.size]
                    vm.saveTask(task.copy(listId = next.id))
                }
                DetailRow(tr("estimate"), task.estimateMin?.let { tr("minutes", it) } ?: tr("notSet")) {
                    val i = ESTIMATES.indexOf(task.estimateMin)
                    vm.saveTask(task.copy(estimateMin = if (i == ESTIMATES.lastIndex) null else ESTIMATES[i + 1]))
                }
                DetailRow(tr("deadline"), task.deadline?.let { shortDate(it, vm.lang) } ?: tr("notSet")) {
                    pickDate(context, task.deadline ?: vm.today()) { d -> vm.saveTask(task.copy(deadline = d, targetDate = task.targetDate?.takeIf { !it.isAfter(d) })) }
                }
                // The early target must be on or before the real deadline (PAR01).
                DetailRow(tr("target"), task.targetDate?.let { shortDate(it, vm.lang) } ?: tr("notSet")) {
                    pickDate(context, task.targetDate ?: vm.today()) { d -> if (task.deadline == null || !d.isAfter(task.deadline)) vm.saveTask(task.copy(targetDate = d)) }
                }
                var tags by remember(task.id) { mutableStateOf(task.tags.joinToString(", ")) }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("tags"), style = T.small)
                    BasicTextField(tags, {
                        tags = it
                        vm.saveTask(task.copy(tags = it.split(',').map(String::trim).filter(String::isNotEmpty).toSet()))
                    }, textStyle = T.label, modifier = Modifier.size(width = 180.dp, height = 24.dp))
                }
            }

            if (source != null) {
                Row(
                    Modifier.fillMaxWidth().card().clickable { vm.go(Screen.Process(source.id)) }.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(kindIcon(source.kind), null, tint = C.primary)
                    Column(Modifier.weight(1f)) {
                        Text(tr("originalNote"), style = T.label)
                        Text(source.title, style = T.micro)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sessions.isEmpty() && rules.isEmpty()) {
                    Text(tr("readyWhenYouAre"), style = T.label)
                    Text(tr("noTimeSet"), style = T.small)
                } else {
                    Text(tr("sessions"), style = T.label)
                    rules.forEach { r ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val rel = when (r.rule.relation) {
                                app.dailylife.core.model.Relation.STARTS_AFTER -> tr("startsAfter")
                                app.dailylife.core.model.Relation.STARTS_BEFORE -> tr("startsBefore")
                                app.dailylife.core.model.Relation.ENDS_BEFORE -> tr("endsBefore")
                            }
                            Text("${tr("every")}: $rel ${prayerName(r.rule.prayer, vm.lang)} · ${num(r.rule.offsetMin)}/${num(r.rule.durationMin)} ${tr("min")}", style = T.small, modifier = Modifier.weight(1f))
                            IconBtn(Icons.Outlined.Close, tr("delete"), { vm.removeRule(r.rule.id) }, C.secondary)
                        }
                    }
                    sessions.take(5).forEach { session ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val date = session.start.atZone(zone).toLocalDate()
                            val label = "${shortDate(date, vm.lang)}, ${num(range(session.start, session.end, zone))}" +
                                (if (session.timing is TimingRule.Relative) " · ↻" else "") +
                                (if (session.pinned) " · ${tr("pinned")}" else "") +
                                (if (session.state == SessionState.NEEDS_REVIEW) " · ${tr("needsReview")}" else "")
                            Text(label, style = T.small.copy(color = if (session.state == SessionState.NEEDS_REVIEW) C.warningText else C.secondary), modifier = Modifier.weight(1f).clickable { vm.scheduleTaskId = task.id + "|" + session.id })
                            IconBtn(Icons.Outlined.Close, tr("delete"), { vm.deleteSession(session) }, C.secondary)
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (fresh) {
                Btn(tr("planSession"), { vm.scheduleTaskId = task.id })
                Btn(tr("continueProcessing"), { vm.replace(Screen.Process()) }, BtnStyle.SECONDARY)
            } else {
                if (!done) Btn(if (s.focus?.taskId == task.id) tr("resume") else tr("startFocus"), { vm.startFocus(task); vm.go(Screen.Focus) })
                Btn(if (sessions.isEmpty()) tr("planSession") else tr("addSession"), { vm.scheduleTaskId = task.id }, BtnStyle.SECONDARY)
            }
        }
    }
}

