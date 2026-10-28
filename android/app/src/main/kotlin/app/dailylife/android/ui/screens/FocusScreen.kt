package app.dailylife.android.ui.screens

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.dailylife.android.AppViewModel
import app.dailylife.android.Screen
import app.dailylife.android.Tab
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.Btn
import app.dailylife.android.ui.BtnStyle
import app.dailylife.android.ui.C
import app.dailylife.android.ui.Empty
import app.dailylife.android.ui.IconBtn
import app.dailylife.android.ui.ScreenColumn
import app.dailylife.android.ui.T
import app.dailylife.android.ui.card
import app.dailylife.android.ui.clock
import app.dailylife.android.ui.hm
import app.dailylife.android.ui.num
import app.dailylife.android.ui.tr
import app.dailylife.core.focus.FocusState
import kotlinx.coroutines.delay
import java.time.Instant

/** Quiet execution screen (DO01). Time running out never completes the task. */
@Composable
fun FocusScreen(vm: AppViewModel, s: Snapshot) {
    val focus = s.focus
    val task = focus?.let { f -> s.tasks.firstOrNull { it.id == f.taskId } }
    var now by remember { mutableStateOf(Instant.now()) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(focus?.state) { while (focus?.state == FocusState.RUNNING || focus?.state == FocusState.BREAK) { now = Instant.now(); delay(500) } }

    ScreenColumn(gap = 20.dp, bottomPadding = 32.dp) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.KeyboardArrowDown, tr("back"), { vm.back() })
            Text(
                when (focus?.state) { FocusState.PAUSED -> tr("sessionPaused"); FocusState.BREAK -> tr("onBreak"); else -> tr("focusSession") },
                style = T.micro,
            )
            IconBtn(Icons.Outlined.MoreHoriz, tr("more"), { menu = !menu })
        }
        if (focus == null || task == null) {
            Empty(Icons.Outlined.Timer, tr("noFocus"))
            return@ScreenColumn
        }
        if (menu) Column(Modifier.fillMaxWidth().card().padding(4.dp)) {
            MenuRow(tr("addFive")) { vm.extendFocus(); menu = false }
            MenuRow(tr("planSession")) { vm.scheduleTaskId = task.id; menu = false }
        }
        Column(Modifier.clickable { vm.go(Screen.TaskDetail(task.id)) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = T.h2)
            if (task.notes.isNotBlank()) Text(task.notes, style = T.small)
        }

        val remaining = focus.remaining(now).seconds
        val progress = (1f - remaining.toFloat() / focus.planned.seconds.coerceAtLeast(1)).coerceIn(0f, 1f)
        val running = focus.state == FocusState.RUNNING
        Box(Modifier.fillMaxWidth().height(294.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(272.dp)) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(C.subtle, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(if (running) C.primary else C.secondary, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(num(clock(remaining)), style = T.timer)
                Text(
                    when {
                        focus.state == FocusState.PAUSED -> tr("pausedNote")
                        focus.state == FocusState.BREAK -> tr("breakNote")
                        remaining < 0 -> tr("overtime")
                        else -> tr("remainingOf", focus.planned.toMinutes())
                    },
                    style = T.small,
                )
                if (running && remaining > 0) Text(tr("endsAt", num(now.plusSeconds(remaining).hm(s.prayer.zone))), style = T.micro)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (focus.state) {
                FocusState.RUNNING -> Btn(tr("pause"), { vm.pauseFocus() })
                else -> Btn(tr("resume"), { vm.resumeFocus() })
            }
            if (focus.state != FocusState.BREAK) Btn(tr("takeBreak"), { vm.breakFocus() }, style = BtnStyle.SECONDARY)
            Btn(tr("endSession"), { vm.endFocus(); vm.back() }, style = BtnStyle.SECONDARY)
            Btn(tr("complete"), { vm.toggleDone(task); vm.tab(Tab.TODAY) }, style = BtnStyle.SECONDARY)
        }
        Text(tr("endingKeepsOpen"), style = T.micro)
    }
}
