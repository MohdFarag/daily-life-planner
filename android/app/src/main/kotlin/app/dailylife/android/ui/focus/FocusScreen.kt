package app.dailylife.android.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.Snapshot
import app.dailylife.android.navigation.Screen
import app.dailylife.android.navigation.Tab
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.AppIconButton
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Empty
import app.dailylife.android.ui.components.MenuRow
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.format.clock
import app.dailylife.android.ui.format.hm
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.android.viewmodel.ScheduleTarget
import app.dailylife.core.focus.FocusSession
import app.dailylife.core.focus.FocusState
import app.dailylife.core.model.Task
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

private const val TICK_MILLIS = 500L

/** Quiet execution screen (DO01). Time running out never completes the task. */
@Composable
fun FocusScreen(vm: AppViewModel, s: Snapshot) {
    val focus = s.focus
    val task = focus?.let { f -> s.tasks.firstOrNull { it.id == f.taskId } }
    var menuOpen by remember { mutableStateOf(false) }

    ScreenColumn(gap = 20.dp, bottomPadding = 32.dp) {
        FocusHeader(
            focus?.state,
            onBack = { vm.navigator.back() },
            onMenu = { menuOpen = !menuOpen },
        )
        if (focus == null || task == null) {
            Empty(Icons.Outlined.Timer, tr("noFocus"))
            return@ScreenColumn
        }
        if (menuOpen) {
            FocusMenu(
                onExtend = { vm.focus.extendFiveMinutes() },
                onPlan = { vm.scheduling = ScheduleTarget(task.id) },
                onClose = { menuOpen = false },
            )
        }
        TaskSummary(task) { vm.navigator.open(Screen.TaskDetail(task.id)) }
        Timer(focus, now = rememberTicker(focus.state, vm::now), zone = s.prayer.zone)
        FocusControls(
            state = focus.state,
            onPause = vm.focus::pause,
            onResume = vm.focus::resume,
            onBreak = vm.focus::takeBreak,
            onEnd = {
                vm.focus.end()
                vm.navigator.back()
            },
            onComplete = {
                vm.tasks.toggleDone(task)
                vm.navigator.switchTo(Tab.TODAY)
            },
        )
        Text(tr("endingKeepsOpen"), style = AppType.micro)
    }
}

/** The current time, refreshed while the timer runs or a break is counting. */
@Composable
private fun rememberTicker(state: FocusState, now: () -> Instant): Instant {
    var current by remember { mutableStateOf(now()) }
    LaunchedEffect(state) {
        while (state == FocusState.RUNNING || state == FocusState.BREAK) {
            current = now()
            delay(TICK_MILLIS)
        }
    }
    return current
}

@Composable
private fun FocusHeader(state: FocusState?, onBack: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(Icons.Outlined.KeyboardArrowDown, tr("back"), onBack)
        val title =
            when (state) {
                FocusState.PAUSED -> tr("sessionPaused")
                FocusState.BREAK -> tr("onBreak")
                else -> tr("focusSession")
            }
        Text(title, style = AppType.micro)
        AppIconButton(Icons.Outlined.MoreHoriz, tr("more"), onMenu)
    }
}

@Composable
private fun FocusMenu(onExtend: () -> Unit, onPlan: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(4.dp)) {
        MenuRow(tr("addFive")) {
            onExtend()
            onClose()
        }
        MenuRow(tr("planSession")) {
            onPlan()
            onClose()
        }
    }
}

@Composable
private fun TaskSummary(task: Task, onOpen: () -> Unit) {
    Column(Modifier.clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(task.title, style = AppType.h2)
        if (task.notes.isNotBlank()) Text(task.notes, style = AppType.small)
    }
}

/** Countdown ring and remaining time; overtime keeps counting as work (TIM02). */
@Composable
private fun Timer(focus: FocusSession, now: Instant, zone: ZoneId) {
    val remaining = focus.remaining(now).seconds
    val running = focus.state == FocusState.RUNNING
    val progress =
        (1f - remaining.toFloat() / focus.planned.seconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(294.dp), contentAlignment = Alignment.Center) {
        ProgressRing(progress, if (running) AppColors.primary else AppColors.secondary)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(num(clock(remaining)), style = AppType.timer)
            val note =
                when {
                    focus.state == FocusState.PAUSED -> tr("pausedNote")
                    focus.state == FocusState.BREAK -> tr("breakNote")
                    remaining < 0 -> tr("overtime")
                    else -> tr("remainingOf", focus.planned.toMinutes())
                }
            Text(note, style = AppType.small)
            if (running && remaining > 0)
                Text(tr("endsAt", num(now.plusSeconds(remaining).hm(zone))), style = AppType.micro)
        }
    }
}

@Composable
private fun ProgressRing(progress: Float, color: Color) {
    Canvas(Modifier.size(272.dp)) {
        val stroke = 10.dp.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(AppColors.subtle, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(
            color,
            -90f,
            360f * progress,
            false,
            topLeft,
            arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun FocusControls(
    state: FocusState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onBreak: () -> Unit,
    onEnd: () -> Unit,
    onComplete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state == FocusState.RUNNING) AppButton(tr("pause"), onPause)
        else AppButton(tr("resume"), onResume)
        if (state != FocusState.BREAK)
            AppButton(tr("takeBreak"), onBreak, style = ButtonStyle.SECONDARY)
        AppButton(tr("endSession"), onEnd, style = ButtonStyle.SECONDARY)
        AppButton(tr("complete"), onComplete, style = ButtonStyle.SECONDARY)
    }
}
