package app.dailylife.android.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.Snapshot
import app.dailylife.android.navigation.Screen
import app.dailylife.android.ui.components.Corner12
import app.dailylife.android.ui.components.Corner8
import app.dailylife.android.ui.format.blockLabel
import app.dailylife.android.ui.format.range
import app.dailylife.android.ui.i18n.LocalLang
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.BlockPolicy
import app.dailylife.core.model.TimeBlock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Height of one hour on the timeline (matches the Figma day view). */
private const val HOUR_HEIGHT_DP = 74.4f
private const val FIRST_HOUR = 5
private const val LAST_HOUR = 23
private const val SCROLL_TO_HOUR = 9
private val LABEL_WIDTH = 58.dp

/** Maps clock times on [date] to vertical positions (in dp) on the timeline. */
private class TimelineScale(private val date: LocalDate, private val zone: ZoneId) {
    val totalHeight: Float = (LAST_HOUR - FIRST_HOUR) * HOUR_HEIGHT_DP

    fun y(time: LocalTime): Float =
        ((time.toSecondOfDay() / 60f - FIRST_HOUR * 60) / 60f * HOUR_HEIGHT_DP).coerceIn(
            0f,
            totalHeight,
        )

    /** Instants after midnight of [date] pin to the bottom. */
    fun y(instant: Instant): Float {
        val local = instant.atZone(zone)
        return if (local.toLocalDate().isAfter(date)) totalHeight else y(local.toLocalTime())
    }
}

/** The day as a scrollable timeline: blocks underneath, hour grid, items, and the current time. */
@Composable
internal fun DayTimeline(vm: AppViewModel, s: Snapshot, date: LocalDate, items: List<PlanItem>) {
    val scale = TimelineScale(date, s.prayer.zone)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(date) {
        scroll.scrollTo(
            with(density) { ((SCROLL_TO_HOUR - FIRST_HOUR) * HOUR_HEIGHT_DP).dp.roundToPx() }
        )
    }

    Box(Modifier.fillMaxWidth().height(372.dp).clip(Corner12).verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().height((scale.totalHeight + 12).dp)) {
            vm.dayPlanner.dayContext(s, date).activeBlocks.forEach { BlockBackground(it, scale) }
            HourGrid()
            items.forEach { item ->
                TimelineItem(
                    item,
                    scale,
                    s.prayer.zone,
                    onOpenTask = { vm.navigator.open(Screen.TaskDetail(it)) },
                )
            }
            if (date == vm.today()) NowLine(scale.y(vm.now()))
        }
    }
}

/** A time block as a light background beneath sessions (BLK01). */
@Composable
private fun BlockBackground(block: TimeBlock, scale: TimelineScale) {
    val top = scale.y(block.start)
    val label =
        blockLabel(block, LocalLang.current).uppercase() +
            if (block.policy == BlockPolicy.PROTECTED) " · ${tr("protected")}" else ""
    Box(
        Modifier.offset(x = LABEL_WIDTH, y = top.dp)
            .fillMaxWidth()
            .padding(end = 60.dp)
            .height((scale.y(block.end) - top).dp)
            .clip(Corner12)
            .background(Color(block.color).copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = AppType.micro)
    }
}

@Composable
private fun HourGrid() {
    (FIRST_HOUR..LAST_HOUR).forEach { hour ->
        val top = ((hour - FIRST_HOUR) * HOUR_HEIGHT_DP).dp
        Text(
            num("%02d:00".format(hour)),
            style = AppType.micro,
            modifier = Modifier.offset(y = top - 9.dp).width(48.dp),
        )
        Box(
            Modifier.offset(x = LABEL_WIDTH, y = top)
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColors.border)
        )
    }
}

@Composable
private fun TimelineItem(
    item: PlanItem,
    scale: TimelineScale,
    zone: ZoneId,
    onOpenTask: (String) -> Unit,
) {
    val top = scale.y(item.start)
    val height = (scale.y(item.end) - top - 2).coerceAtLeast(22f)
    val (background, foreground) = colorsFor(item)
    val taskId = item.taskId
    Column(
        Modifier.offset(x = 62.dp, y = (top + 1).dp)
            .fillMaxWidth()
            .padding(end = 62.dp)
            .height(height.dp)
            .clip(Corner8)
            .background(background)
            .then(if (taskId != null) Modifier.clickable { onOpenTask(taskId) } else Modifier)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val time = num(range(item.start, item.end, zone))
        if (item.kind == PlanItemKind.TASK && height > 44) {
            val decoration = if (item.done) TextDecoration.LineThrough else null
            Text(
                item.label(),
                style = AppType.label.copy(color = foreground, textDecoration = decoration),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(time, style = AppType.micro.copy(color = foreground))
        } else {
            Text(
                "$time · ${item.label()}",
                style = AppType.micro.copy(color = foreground),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Background and text colors: done and needs-review tasks are muted or highlighted. */
private fun colorsFor(item: PlanItem): Pair<Color, Color> =
    when (item.kind) {
        PlanItemKind.TASK ->
            when {
                item.done -> AppColors.tint to AppColors.secondary
                item.needsReview -> AppColors.warning to AppColors.warningText
                else -> AppColors.primary to AppColors.onPrimary
            }
        PlanItemKind.PRAYER -> AppColors.tint to AppColors.primary
        PlanItemKind.EVENT -> AppColors.warning to AppColors.warningText
    }

@Composable
private fun NowLine(y: Float) {
    Box(
        Modifier.offset(x = 54.dp, y = y.dp).fillMaxWidth().height(1.5.dp).background(AppColors.now)
    )
}
