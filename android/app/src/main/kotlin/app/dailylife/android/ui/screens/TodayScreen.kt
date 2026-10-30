package app.dailylife.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.dailylife.android.AppViewModel
import app.dailylife.android.Screen
import app.dailylife.android.Tab
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.cityLabel
import app.dailylife.android.ui.listName
import app.dailylife.android.ui.Badge
import app.dailylife.android.ui.Btn
import app.dailylife.android.ui.BtnStyle
import app.dailylife.android.ui.C
import app.dailylife.android.ui.PageHeading
import app.dailylife.android.ui.PrayerRow
import app.dailylife.android.ui.R24
import app.dailylife.android.ui.ScreenColumn
import app.dailylife.android.ui.T
import app.dailylife.android.ui.TaskRowView
import app.dailylife.android.ui.card
import app.dailylife.android.ui.dayLabel
import app.dailylife.android.ui.duration
import app.dailylife.android.ui.hm
import app.dailylife.android.ui.num
import app.dailylife.android.ui.prayerName
import app.dailylife.android.ui.range
import app.dailylife.android.ui.tr
import app.dailylife.core.inbox.Triage
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskStatus
import app.dailylife.core.schedule.Planner
import app.dailylife.core.time.Interval
import java.time.Instant
import java.time.LocalTime

@Composable
fun TodayScreen(vm: AppViewModel, s: Snapshot) {
    val zone = s.prayer.zone
    val today = vm.today()
    val now = Instant.now()
    val lang = s.prefs.lang
    val dayStart = today.atStartOfDay(zone).toInstant()
    val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
    val tasksById = s.tasks.associateBy { it.id }

    val todaysSessions = s.sessions
        .filter { it.state != SessionState.CANCELED && it.start >= dayStart && it.start < dayEnd && it.taskId in tasksById }
        .sortedBy { it.start }
    // Progress counts each task once, even with several sessions (AC07).
    val todaysTaskIds = (todaysSessions.map { it.taskId } + s.tasks.filter { it.completedAt?.atZone(zone)?.toLocalDate() == today }.map { it.id }).toSet()
    val done = todaysTaskIds.count { tasksById[it]?.status == TaskStatus.COMPLETED }
    val total = todaysTaskIds.size

    val upNext = todaysSessions.firstOrNull { tasksById.getValue(it.taskId).status != TaskStatus.COMPLETED && it.end.isAfter(now) }
    val rest = todaysSessions.filter { it != upNext }
    val anchors = vm.repo.anchors(today, s)
    val lastEnd = todaysSessions.maxOfOrNull { it.end } ?: now
    val prayers = anchors.filter { it.end.isAfter(now) && it.adhan.isBefore(lastEnd) }.ifEmpty { anchors.filter { it.end.isAfter(now) }.take(1) }
    val followUps = Triage.followUpsDue(s.items, today)

    val ctx = vm.repo.dayContext(s, today)
    val workdayEnd = today.atTime(LocalTime.of(21, 0)).atZone(zone).toInstant()
    val free = if (now.isBefore(workdayEnd)) Planner.freeTime(ctx, Interval(maxOf(now, today.atTime(9, 0).atZone(zone).toInstant()).coerceAtMost(workdayEnd), workdayEnd)) else java.time.Duration.ZERO

    ScreenColumn {
        PageHeading(tr("today"), dayLabel(today, lang)) { vm.go(Screen.Search) }

        if (!s.prefs.prayerConfirmed) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.warning).clickable { vm.go(Screen.PrayerSettings) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.LocationOn, null, tint = C.warningText)
                Text(tr("setCity") + " · ${cityLabel(s.prayer, lang)}", style = T.label, color = C.warningText)
            }
        }

        Row(Modifier.fillMaxWidth().card(RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr("tasksComplete", done, total), style = T.label, color = C.primary)
            Box(Modifier.width(100.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.subtle)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (total == 0) 0f else done.toFloat() / total).clip(RoundedCornerShape(3.dp)).background(C.primary))
            }
        }

        if (upNext != null) {
            val task = tasksById.getValue(upNext.taskId)
            val focused = s.focus?.taskId == task.id
            Column(Modifier.fillMaxWidth().clip(R24).background(C.primary).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("upNext"), style = T.micro, color = C.onPrimary)
                    Badge(num(range(upNext.start, upNext.end, zone)))
                }
                Column(Modifier.clickable { vm.go(Screen.TaskDetail(task.id)) }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(task.title, style = T.h2, color = C.onPrimary)
                    val list = listName(s, task.listId)
                    Text(listOfNotNull(list, task.notes.takeIf { it.isNotBlank() }).joinToString(" · "), style = T.small, color = C.onPrimary)
                }
                Btn(if (focused) tr("resume") else tr("startSession"), { vm.startFocus(task); vm.go(Screen.Focus) }, style = BtnStyle.LIGHT)
            }
        } else {
            Column(Modifier.fillMaxWidth().clip(R24).background(C.tint).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("nothingNext"), style = T.h3, color = C.primary)
                Text(tr("nothingNextHint"), style = T.small)
                Btn(tr("openPlan"), { vm.tab(Tab.PLAN) }, style = BtnStyle.SECONDARY)
            }
        }

        if (followUps.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.subtle).clickable { vm.go(Screen.Collections) }.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = C.primary)
                Text(tr("followUpsDue", followUps.size), style = T.label, color = C.primary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp).clickable { vm.tab(Tab.PLAN) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("yourDay"), style = T.h3)
                Text(tr("viewPlan"), style = T.label, color = C.primary)
            }
            val rows = prayers.map { it.adhan to "p" + it.prayer.name } + rest.map { it.start to "s" + it.id }
            rows.sortedBy { it.first }.forEach { (_, key) ->
                if (key.startsWith("p")) {
                    val a = prayers.first { "p" + it.prayer.name == key }
                    PrayerRow(tr("prayerOf", prayerName(a.prayer, lang)), num(range(a.adhan, a.end, zone)))
                } else {
                    val session = rest.first { "s" + it.id == key }
                    val task = tasksById.getValue(session.taskId)
                    val list = listName(s, task.listId).orEmpty()
                    val minutes = java.time.Duration.between(session.start, session.end).toMinutes()
                    val meta = listOf(num(session.start.hm(zone)), "${num(minutes)} ${tr("min")}", list).filter { it.isNotEmpty() }.joinToString(" · ") +
                        if (session.state == SessionState.NEEDS_REVIEW) " · ${tr("needsReview")}" else ""
                    TaskRowView(task.title, meta, task.status == TaskStatus.COMPLETED, { vm.toggleDone(task) }, { vm.go(Screen.TaskDetail(task.id)) })
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.AccessTime, null, tint = C.secondary, modifier = Modifier.size(16.dp))
            Text(if (free.isZero) tr("dayFull") else tr("leftUnplanned", duration(free, lang)), style = T.micro)
        }
    }
}
