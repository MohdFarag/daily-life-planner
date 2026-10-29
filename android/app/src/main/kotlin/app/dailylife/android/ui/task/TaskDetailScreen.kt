package app.dailylife.android.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.RuleBinding
import app.dailylife.android.data.Snapshot
import app.dailylife.android.navigation.Screen
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.AppIconButton
import app.dailylife.android.ui.components.Badge
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.DetailRow
import app.dailylife.android.ui.components.MenuRow
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.TopBar
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.components.kindIcon
import app.dailylife.android.ui.components.pickDate
import app.dailylife.android.ui.format.listName
import app.dailylife.android.ui.format.prayerName
import app.dailylife.android.ui.format.range
import app.dailylife.android.ui.format.shortDate
import app.dailylife.android.ui.i18n.LocalLang
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.android.viewmodel.ScheduleTarget
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.Relation
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TaskStatus
import app.dailylife.core.model.TimingRule
import java.time.ZoneId

@Composable
fun TaskDetailScreen(vm: AppViewModel, s: Snapshot, id: String, fresh: Boolean) {
    val task = s.tasks.firstOrNull { it.id == id }
    if (task == null) {
        LaunchedEffect(Unit) { vm.navigator.back() }
        return
    }
    val upcoming =
        s.sessions
            .filter {
                it.taskId == task.id &&
                    it.state != SessionState.CANCELED &&
                    it.end.isAfter(vm.now())
            }
            .sortedBy { it.start }
    val rules = s.rules.filter { it.taskId == task.id }
    val save: (Task) -> Unit = { vm.tasks.save(it) }

    Column(Modifier.fillMaxSize()) {
        ScreenColumn(Modifier.weight(1f), bottomPadding = 16.dp) {
            TaskTopBar(vm, task, fresh)
            TitleAndNotes(task, save)
            StatusBadges(task, save)
            DetailsCard(task, s, vm, save)
            s.items
                .firstOrNull { it.id == task.sourceItemId }
                ?.let { source ->
                    SourceLink(source) { vm.navigator.open(Screen.Process(source.id)) }
                }
            SessionsCard(rules, upcoming.take(5), s.prayer.zone, vm, task)
        }
        BottomActions(vm, s, task, fresh, hasSessions = upcoming.isNotEmpty())
    }
}

@Composable
private fun TaskTopBar(vm: AppViewModel, task: Task, fresh: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    TopBar(if (fresh) tr("newTask") else tr("task"), { vm.navigator.back() }) {
        AppIconButton(Icons.Outlined.MoreHoriz, tr("more"), { menuOpen = !menuOpen })
    }
    if (menuOpen) {
        Column(Modifier.fillMaxWidth().card().padding(4.dp)) {
            MenuRow(if (task.status == TaskStatus.COMPLETED) tr("reopen") else tr("complete")) {
                vm.tasks.toggleDone(task)
                menuOpen = false
            }
            MenuRow(tr("deleteTask"), danger = true) { vm.tasks.delete(task) }
        }
    }
}

@Composable
private fun TitleAndNotes(task: Task, save: (Task) -> Unit) {
    Column {
        BasicTextField(
            task.title,
            { save(task.copy(title = it)) },
            textStyle = AppType.h1,
            modifier = Modifier.fillMaxWidth(),
        )
        Box {
            if (task.notes.isEmpty()) Text(tr("notesHint"), style = AppType.small)
            BasicTextField(
                task.notes,
                { save(task.copy(notes = it)) },
                textStyle = AppType.small,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Tappable badges toggling importance and "in progress". */
@Composable
private fun StatusBadges(task: Task, save: (Task) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (task.important) {
            Badge("★ " + tr("important"), AppColors.warning, AppColors.warningText) {
                save(task.copy(important = false))
            }
        } else {
            Badge(tr("markImportant"), AppColors.subtle, AppColors.secondary) {
                save(task.copy(important = true))
            }
        }
        when (task.status) {
            TaskStatus.IN_PROGRESS ->
                Badge(tr("inProgress"), AppColors.tint, AppColors.primary) {
                    save(task.copy(status = TaskStatus.NEEDS_ACTION))
                }
            TaskStatus.COMPLETED -> Badge(tr("completed"), AppColors.tint, AppColors.primary)
            TaskStatus.NEEDS_ACTION ->
                Badge(tr("inProgress") + "?", AppColors.subtle, AppColors.secondary) {
                    save(task.copy(status = TaskStatus.IN_PROGRESS))
                }
        }
    }
}

@Composable
private fun DetailsCard(task: Task, s: Snapshot, vm: AppViewModel, save: (Task) -> Unit) {
    val context = LocalContext.current
    val lang = s.lang
    Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 8.dp)) {
        DetailRow(tr("listLabel"), listName(s, task.listId) ?: "—") {
            save(TaskEdits.nextList(task, s.lists))
        }
        DetailRow(tr("estimate"), task.estimateMin?.let { tr("minutes", it) } ?: tr("notSet")) {
            save(TaskEdits.nextEstimate(task))
        }
        DetailRow(tr("deadline"), task.deadline?.let { shortDate(it, lang) } ?: tr("notSet")) {
            pickDate(context, task.deadline ?: vm.today()) {
                save(TaskEdits.withDeadline(task, it))
            }
        }
        DetailRow(tr("target"), task.targetDate?.let { shortDate(it, lang) } ?: tr("notSet")) {
            pickDate(context, task.targetDate ?: vm.today()) { date ->
                TaskEdits.withTarget(task, date)?.let(save)
            }
        }
        TagsField(task, save)
    }
}

@Composable
private fun TagsField(task: Task, save: (Task) -> Unit) {
    var text by remember(task.id) { mutableStateOf(task.tags.joinToString(", ")) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("tags"), style = AppType.small)
        BasicTextField(
            text,
            {
                text = it
                save(TaskEdits.withTags(task, it))
            },
            textStyle = AppType.label,
            modifier = Modifier.size(width = 180.dp, height = 24.dp),
        )
    }
}

/** Link back to the inbox item this task came from. */
@Composable
private fun SourceLink(source: CaptureItem, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().card().clickable(onClick = onOpen).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kindIcon(source.kind), null, tint = AppColors.primary)
        Column(Modifier.weight(1f)) {
            Text(tr("originalNote"), style = AppType.label)
            Text(source.title, style = AppType.micro)
        }
    }
}

@Composable
private fun SessionsCard(
    rules: List<RuleBinding>,
    sessions: List<TaskSession>,
    zone: ZoneId,
    vm: AppViewModel,
    task: Task,
) {
    Column(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sessions.isEmpty() && rules.isEmpty()) {
            Text(tr("readyWhenYouAre"), style = AppType.label)
            Text(tr("noTimeSet"), style = AppType.small)
            return@Column
        }
        Text(tr("sessions"), style = AppType.label)
        rules.forEach { binding -> RuleRow(binding.rule) { vm.tasks.detachRule(binding.rule.id) } }
        sessions.forEach { session ->
            SessionRow(
                session,
                zone,
                onEdit = { vm.scheduling = ScheduleTarget(task.id, session) },
                onDelete = { vm.tasks.deleteSession(session) },
            )
        }
    }
}

@Composable
private fun RuleRow(rule: RelativeRule, onRemove: () -> Unit) {
    val relation =
        when (rule.relation) {
            Relation.STARTS_AFTER -> tr("startsAfter")
            Relation.STARTS_BEFORE -> tr("startsBefore")
            Relation.ENDS_BEFORE -> tr("endsBefore")
        }
    val prayer = prayerName(rule.prayer, LocalLang.current)
    val text =
        "${tr("every")}: $relation $prayer · ${num(rule.offsetMin)}/${num(rule.durationMin)} ${tr("min")}"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = AppType.small, modifier = Modifier.weight(1f))
        AppIconButton(Icons.Outlined.Close, tr("delete"), onRemove, AppColors.secondary)
    }
}

@Composable
private fun SessionRow(
    session: TaskSession,
    zone: ZoneId,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val needsReview = session.state == SessionState.NEEDS_REVIEW
    val notes =
        listOfNotNull(
            "↻".takeIf { session.timing is TimingRule.Relative },
            tr("pinned").takeIf { session.pinned },
            tr("needsReview").takeIf { needsReview },
        )
    val text =
        (listOf(
                "${shortDate(session.start.atZone(zone).toLocalDate(), LocalLang.current)}, ${num(range(session.start, session.end, zone))}"
            ) + notes)
            .joinToString(" · ")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style =
                AppType.small.copy(
                    color = if (needsReview) AppColors.warningText else AppColors.secondary
                ),
            modifier = Modifier.weight(1f).clickable(onClick = onEdit),
        )
        AppIconButton(Icons.Outlined.Close, tr("delete"), onDelete, AppColors.secondary)
    }
}

/** A task fresh from triage offers planning or moving on; otherwise focus and scheduling. */
@Composable
private fun BottomActions(
    vm: AppViewModel,
    s: Snapshot,
    task: Task,
    fresh: Boolean,
    hasSessions: Boolean,
) {
    Column(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val schedule = { vm.scheduling = ScheduleTarget(task.id) }
        if (fresh) {
            AppButton(tr("planSession"), schedule)
            AppButton(
                tr("continueProcessing"),
                { vm.navigator.replace(Screen.Process()) },
                style = ButtonStyle.SECONDARY,
            )
            return@Column
        }
        if (task.status != TaskStatus.COMPLETED) {
            val focusing = s.focus?.taskId == task.id
            AppButton(
                if (focusing) tr("resume") else tr("startFocus"),
                {
                    vm.focus.start(task)
                    vm.navigator.open(Screen.Focus)
                },
            )
        }
        AppButton(
            if (hasSessions) tr("addSession") else tr("planSession"),
            schedule,
            style = ButtonStyle.SECONDARY,
        )
    }
}
