package app.dailylife.android.ui.today

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
import app.dailylife.android.data.Snapshot
import app.dailylife.android.navigation.Screen
import app.dailylife.android.navigation.Tab
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.Badge
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Corner12
import app.dailylife.android.ui.components.Corner24
import app.dailylife.android.ui.components.PageHeading
import app.dailylife.android.ui.components.PrayerRow
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.TaskRowView
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.format.cityLabel
import app.dailylife.android.ui.format.dayLabel
import app.dailylife.android.ui.format.duration
import app.dailylife.android.ui.format.hm
import app.dailylife.android.ui.format.listName
import app.dailylife.android.ui.format.prayerName
import app.dailylife.android.ui.format.range
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskStatus
import java.time.Duration

@Composable
fun TodayScreen(vm: AppViewModel, s: Snapshot) {
    val today = vm.today()
    val state = buildTodayState(s, vm.dayPlanner, today, vm.now())

    ScreenColumn {
        PageHeading(tr("today"), dayLabel(today, s.lang)) { vm.navigator.open(Screen.Search) }
        if (!s.prefs.prayerConfirmed)
            SetCityBanner(cityLabel(s.prayer, s.lang)) { vm.navigator.open(Screen.PrayerSettings) }
        ProgressCard(state.tasksDone, state.tasksTotal)

        val upNext = state.upNext
        if (upNext != null) {
            UpNextCard(
                item = upNext,
                snapshot = s,
                focused = s.focus?.taskId == upNext.task.id,
                onOpen = { vm.navigator.open(Screen.TaskDetail(upNext.task.id)) },
                onStart = {
                    vm.focus.start(upNext.task)
                    vm.navigator.open(Screen.Focus)
                },
            )
        } else {
            NothingNextCard { vm.navigator.switchTo(Tab.PLAN) }
        }

        if (state.followUpsDue > 0)
            FollowUpsBanner(state.followUpsDue) { vm.navigator.open(Screen.Collections) }
        YourDay(state.rows, s, vm)
        FreeTimeLine(state.freeTime, s.lang)
    }
}

@Composable
private fun SetCityBanner(city: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(Corner12)
            .background(AppColors.warning)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.LocationOn, null, tint = AppColors.warningText)
        Text(tr("setCity") + " · $city", style = AppType.label, color = AppColors.warningText)
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().card(Corner12).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(tr("tasksComplete", done, total), style = AppType.label, color = AppColors.primary)
        val bar = RoundedCornerShape(3.dp)
        Box(Modifier.width(100.dp).height(5.dp).clip(bar).background(AppColors.subtle)) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(if (total == 0) 0f else done.toFloat() / total)
                    .clip(bar)
                    .background(AppColors.primary)
            )
        }
    }
}

@Composable
private fun UpNextCard(
    item: ScheduledTask,
    snapshot: Snapshot,
    focused: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
) {
    val task = item.task
    Column(
        Modifier.fillMaxWidth().clip(Corner24).background(AppColors.primary).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(tr("upNext"), style = AppType.micro, color = AppColors.onPrimary)
            Badge(num(range(item.session.start, item.session.end, snapshot.prayer.zone)))
        }
        Column(
            Modifier.clickable(onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(task.title, style = AppType.h2, color = AppColors.onPrimary)
            val details =
                listOfNotNull(
                    listName(snapshot, task.listId),
                    task.notes.takeIf { it.isNotBlank() },
                )
            Text(details.joinToString(" · "), style = AppType.small, color = AppColors.onPrimary)
        }
        AppButton(
            if (focused) tr("resume") else tr("startSession"),
            onStart,
            style = ButtonStyle.LIGHT,
        )
    }
}

@Composable
private fun NothingNextCard(onOpenPlan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(Corner24).background(AppColors.tint).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("nothingNext"), style = AppType.h3, color = AppColors.primary)
        Text(tr("nothingNextHint"), style = AppType.small)
        AppButton(tr("openPlan"), onOpenPlan, style = ButtonStyle.SECONDARY)
    }
}

@Composable
private fun FollowUpsBanner(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(Corner12)
            .background(AppColors.subtle)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.NotificationsActive, null, tint = AppColors.primary)
        Text(tr("followUpsDue", count), style = AppType.label, color = AppColors.primary)
    }
}

@Composable
private fun YourDay(rows: List<DayRow>, s: Snapshot, vm: AppViewModel) {
    val zone = s.prayer.zone
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clickable { vm.navigator.switchTo(Tab.PLAN) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(tr("yourDay"), style = AppType.h3)
            Text(tr("viewPlan"), style = AppType.label, color = AppColors.primary)
        }
        rows.forEach { row ->
            when (row) {
                is DayRow.Prayer ->
                    PrayerRow(
                        tr("prayerOf", prayerName(row.anchor.prayer, s.lang)),
                        num(range(row.anchor.adhan, row.anchor.end, zone)),
                    )
                is DayRow.Session -> {
                    val (session, task) = row.item
                    val minutes = Duration.between(session.start, session.end).toMinutes()
                    val meta =
                        listOfNotNull(
                                num(session.start.hm(zone)),
                                "${num(minutes)} ${tr("min")}",
                                listName(s, task.listId),
                            )
                            .joinToString(" · ") +
                            if (session.state == SessionState.NEEDS_REVIEW)
                                " · ${tr("needsReview")}"
                            else ""
                    TaskRowView(
                        task.title,
                        meta,
                        task.status == TaskStatus.COMPLETED,
                        onToggle = { vm.tasks.toggleDone(task) },
                        onOpen = { vm.navigator.open(Screen.TaskDetail(task.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FreeTimeLine(free: Duration, lang: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.AccessTime,
            null,
            tint = AppColors.secondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            if (free.isZero) tr("dayFull") else tr("leftUnplanned", duration(free, lang)),
            style = AppType.micro,
        )
    }
}
