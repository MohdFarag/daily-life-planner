package app.dailylife.android.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.Snapshot
import app.dailylife.android.navigation.Screen
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Chip
import app.dailylife.android.ui.components.Corner8
import app.dailylife.android.ui.components.PageHeading
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.Segmented
import app.dailylife.android.ui.components.TaskRowView
import app.dailylife.android.ui.format.listName
import app.dailylife.android.ui.format.locale
import app.dailylife.android.ui.format.prayerName
import app.dailylife.android.ui.i18n.LocalLang
import app.dailylife.android.ui.i18n.localizeDigits
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.android.viewmodel.EventTarget
import app.dailylife.android.viewmodel.ScheduleTarget
import app.dailylife.core.model.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

@Composable
fun PlanScreen(vm: AppViewModel, s: Snapshot) {
    val today = vm.today()
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    var view by rememberSaveable { mutableStateOf(PlanView.DAY.name) }
    val date = LocalDate.parse(selected)
    val drag = remember { TaskDrag() }
    val mode = PlanView.valueOf(view)

    ScreenColumn {
        val month =
            date.month.getDisplayName(TextStyle.FULL_STANDALONE, locale(s.lang)) +
                " " +
                localizeDigits("${date.year}", s.lang)
        PageHeading(tr("plan"), month) { vm.navigator.open(Screen.Search) }
        WeekStrip(s, weekOf(date), selected = date, today = today) { selected = it.toString() }
        ViewSwitcher(mode) { view = it.name }
        AppButton(
            tr("newEvent"),
            { vm.editingEvent = EventTarget(date) },
            style = ButtonStyle.QUIET,
        )

        when (mode) {
            PlanView.DAY -> DayTimeline(vm, s, date, planItems(s, vm.dayPlanner, date), drag)
            PlanView.AGENDA -> Agenda(vm, s, planItems(s, vm.dayPlanner, date))
            PlanView.WEEK ->
                weekOf(date).forEach { day ->
                    WeekDay(vm, s, day, isToday = day == today) {
                        selected = day.toString()
                        view = PlanView.DAY.name
                    }
                }
        }

        Legend()
        UnplannedTasks(
            vm,
            s,
            date,
            unplannedTasks(s, vm.now()),
            drag.takeIf { mode == PlanView.DAY },
        ) { task, time ->
            vm.scheduling = ScheduleTarget(task.id, proposed = date.atTime(time))
        }
    }
}

@Composable
private fun WeekStrip(
    s: Snapshot,
    week: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        week.forEach { day ->
            val isSelected = day == selected
            Column(
                Modifier.weight(1f).height(60.dp).clickable { onSelect(day) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale(s.lang)),
                    style = AppType.micro,
                )
                Box(
                    Modifier.size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) AppColors.primary
                            else if (day == today) AppColors.tint else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        localizeDigits("${day.dayOfMonth}", s.lang),
                        style = AppType.label,
                        color =
                            if (isSelected) AppColors.onPrimary
                            else if (day == today) AppColors.primary else AppColors.text,
                    )
                    if (!isSelected && hasSessionsOn(s, day)) {
                        Box(
                            Modifier.align(Alignment.BottomCenter)
                                .padding(bottom = 3.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(AppColors.secondary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewSwitcher(mode: PlanView, onChange: (PlanView) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Segmented(
            listOf(PlanView.DAY.name to tr("day"), PlanView.WEEK.name to tr("week")),
            mode.name,
            { onChange(PlanView.valueOf(it)) },
            Modifier.width(170.dp),
        )
        Chip(tr("agenda"), mode == PlanView.AGENDA, { onChange(PlanView.AGENDA) })
    }
}

@Composable
private fun WeekDay(
    vm: AppViewModel,
    s: Snapshot,
    day: LocalDate,
    isToday: Boolean,
    onOpenDay: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.FULL, locale(s.lang)) +
                " " +
                localizeDigits("${day.dayOfMonth}", s.lang),
            style = AppType.label.copy(color = if (isToday) AppColors.primary else AppColors.text),
            modifier = Modifier.clickable(onClick = onOpenDay),
        )
        val items = planItems(s, vm.dayPlanner, day).filter { it.kind != PlanItemKind.PRAYER }
        if (items.isEmpty()) Text(tr("emptyDay"), style = AppType.micro) else Agenda(vm, s, items)
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(
                AppColors.primary to tr("tasks"),
                AppColors.tint to tr("prayers"),
                AppColors.warningText to tr("events"),
            )
            .forEach { (color, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, AppColors.primary.copy(alpha = 0.3f), CircleShape)
                    )
                    Text(label, style = AppType.micro)
                }
            }
    }
}

@Composable
private fun UnplannedTasks(
    vm: AppViewModel,
    s: Snapshot,
    date: LocalDate,
    tasks: List<Task>,
    drag: TaskDrag?,
    onDrop: (Task, LocalTime) -> Unit,
) {
    if (tasks.isEmpty()) return
    val density = LocalDensity.current
    val scale = TimelineScale(date, s.prayer.zone)
    Text(tr("notPlanned"), style = AppType.h3)
    if (drag != null) Text(tr("dragToPlan"), style = AppType.micro)
    tasks.forEach { task ->
        var origin by remember { mutableStateOf(Offset.Zero) }
        val draggable =
            if (drag == null) Modifier
            else
                Modifier.onGloballyPositioned { origin = it.positionInRoot() }
                    .pointerInput(task.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                drag.task = task
                                drag.pointer = origin + it
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                drag.pointer += amount
                            },
                            onDragEnd = {
                                drag.dropTime(scale, density)?.let { onDrop(task, it) }
                                drag.task = null
                            },
                            onDragCancel = { drag.task = null },
                        )
                    }
        val meta =
            listOfNotNull(
                    task.estimateMin?.let { "${num(it)} ${tr("min")}" },
                    listName(s, task.listId),
                )
                .joinToString(" · ")
        Box(draggable) {
            TaskRowView(
                task.title,
                meta,
                false,
                { vm.tasks.toggleDone(task) },
                { vm.navigator.open(Screen.TaskDetail(task.id)) },
            ) {
                // Also the alternative to dragging (UX02).
                Text(
                    tr("planSession"),
                    style = AppType.label,
                    color = AppColors.primary,
                    modifier =
                        Modifier.clip(Corner8)
                            .clickable { vm.scheduling = ScheduleTarget(task.id) }
                            .padding(8.dp),
                )
            }
        }
    }
}

/** What a plan item is called: its title, or the localized prayer name. */
@Composable
internal fun PlanItem.label(): String =
    title ?: tr("prayerOf", prayerName(prayer!!, LocalLang.current))
