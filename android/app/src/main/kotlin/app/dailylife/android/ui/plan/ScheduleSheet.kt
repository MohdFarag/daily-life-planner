package app.dailylife.android.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.AppSheet
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Chip
import app.dailylife.android.ui.components.Corner16
import app.dailylife.android.ui.components.Segmented
import app.dailylife.android.ui.components.SheetHeader
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.components.pickDate
import app.dailylife.android.ui.components.pickTime
import app.dailylife.android.ui.format.anchorKey
import app.dailylife.android.ui.format.blockLabel
import app.dailylife.android.ui.format.dayLabel
import app.dailylife.android.ui.format.prayerName
import app.dailylife.android.ui.format.range
import app.dailylife.android.ui.format.relationKey
import app.dailylife.android.ui.i18n.LocalLang
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.i18n.translate
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.android.viewmodel.ScheduleTarget
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation
import app.dailylife.core.schedule.CheckResult
import app.dailylife.core.schedule.Conflict
import app.dailylife.core.time.Interval
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Plan a session: fixed time, or relative to a prayer with explicit wording (REL01). Conflicts are
 * explained before saving; scheduling over them needs an explicit override (PLN02, AC11).
 */
@Composable
fun ScheduleSheet(vm: AppViewModel, s: Snapshot, target: ScheduleTarget) {
    val task = s.tasks.firstOrNull { it.id == target.taskId } ?: return
    val editing = target.editing
    var draft by remember {
        mutableStateOf(ScheduleDraft.initial(task, editing, s, vm.today(), vm.now()))
    }
    val result = evaluateSchedule(draft, task, editing, s, vm.dayPlanner, vm.now())
    val close = { vm.scheduling = null }

    fun save() {
        val slot = result.slot
        if (draft.mode == ScheduleMode.FIXED && slot != null) {
            vm.tasks.saveFixedSession(task.id, slot.start, slot.end, editing)
        } else {
            vm.tasks.attachRule(task.id, draft.rule(id = "rule-" + UUID.randomUUID()))
        }
        close()
    }

    AppSheet(onDismiss = close) {
        SheetHeader(tr("planSession"), onClose = close)
        Text(
            task.title + (task.estimateMin?.let { " · " + tr("minutes", it) } ?: ""),
            style = AppType.small,
        )
        if (editing == null) ModeSwitch(draft.mode) { draft = draft.copy(mode = it) }
        DateField(draft.date, minDate = vm.today()) { draft = draft.copy(date = it) }
        when (draft.mode) {
            ScheduleMode.FIXED -> FixedTimeFields(draft) { draft = it }
            ScheduleMode.RELATIVE -> RelativeFields(draft) { draft = it }
        }
        result.slot?.let { SlotPreview(it, draft, result.check, s.prayer.zone) }
        result.check?.let { Availability(it, s) }
        Suggestions(result.suggestions, s.prayer.zone) {
            draft = draft.copy(start = it.start.atZone(s.prayer.zone).toLocalTime())
        }
        Actions(
            fits = result.check?.ok ?: true,
            canSave = result.slot != null,
            onSave = ::save,
            onCancel = close,
        )
        Text(tr("multiSession"), style = AppType.micro)
    }
}

@Composable
private fun ModeSwitch(mode: ScheduleMode, onChange: (ScheduleMode) -> Unit) {
    Segmented(
        listOf(
            ScheduleMode.FIXED.name to tr("fixedTime"),
            ScheduleMode.RELATIVE.name to tr("relativeToPrayer"),
        ),
        mode.name,
        { onChange(ScheduleMode.valueOf(it)) },
        Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DateField(date: LocalDate, minDate: LocalDate, onPick: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .card()
            .clickable { pickDate(context, date, minDate, onPick) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = AppColors.primary)
        Text(dayLabel(date, LocalLang.current), style = AppType.label)
    }
}

@Composable
private fun FixedTimeFields(draft: ScheduleDraft, onChange: (ScheduleDraft) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeBox(tr("startTime"), num(draft.start.toString()), Modifier.weight(1f)) {
            pickTime(context, draft.start) { onChange(draft.copy(start = it)) }
        }
        TimeBox(tr("duration"), tr("minutes", draft.minutes), Modifier.weight(1f)) {
            onChange(draft.nextDuration())
        }
    }
}

@Composable
private fun RelativeFields(draft: ScheduleDraft, onChange: (ScheduleDraft) -> Unit) {
    val lang = LocalLang.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PrayerName.entries.forEach { prayer ->
            Chip(
                prayerName(prayer, lang),
                draft.prayer == prayer,
                { onChange(draft.copy(prayer = prayer)) },
            )
        }
    }
    Segmented(
        Relation.entries.map { it.name to tr(relationKey(it)) },
        draft.relation.name,
        { onChange(draft.copy(relation = Relation.valueOf(it))) },
        Modifier.fillMaxWidth(),
    )
    Segmented(
        PrayerPoint.entries.map { it.name to tr(anchorKey(it)) },
        draft.anchor.name,
        { onChange(draft.copy(anchor = PrayerPoint.valueOf(it))) },
        Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeBox(tr("offset"), tr("minutes", draft.offsetMinutes), Modifier.weight(1f)) {
            onChange(draft.nextOffset())
        }
        TimeBox(tr("duration"), tr("minutes", draft.minutes), Modifier.weight(1f)) {
            onChange(draft.nextDuration())
        }
    }
    Chip(
        tr("repeatDaily"),
        draft.repeatDaily,
        { onChange(draft.copy(repeatDaily = !draft.repeatDaily)) },
    )
}

@Composable
private fun SlotPreview(slot: Interval, draft: ScheduleDraft, check: CheckResult?, zone: ZoneId) {
    val lang = LocalLang.current
    Column(
        Modifier.fillMaxWidth()
            .clip(Corner16)
            .background(AppColors.tint)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            num(range(slot.start, slot.end, zone)),
            style = AppType.h2.copy(color = AppColors.primary),
        )
        val details =
            listOfNotNull(
                dayLabel(draft.date, lang),
                check?.insideBlock?.let { blockLabel(it, lang) },
                if (draft.mode == ScheduleMode.RELATIVE && draft.repeatDaily) tr("every") else null,
            )
        Text(details.joinToString(" · "), style = AppType.micro.copy(color = AppColors.primary))
    }
}

@Composable
private fun Availability(check: CheckResult, s: Snapshot) {
    val lang = LocalLang.current
    val color = if (check.ok) AppColors.primary else AppColors.warningText
    val inside = check.insideBlock
    val verdict =
        when {
            !check.ok ->
                tr("conflict", check.conflicts.joinToString(", ") { conflictLabel(it, s, lang) })
            inside != null -> tr("fitsBlock", blockLabel(inside, lang))
            else -> tr("free")
        }
    val advice =
        check.advice.joinToString("") {
            " " + translate(lang, "blockAdvice", blockLabel(it.block, lang)) + "."
        }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (check.ok) Icons.Outlined.Check else Icons.Outlined.WarningAmber,
            null,
            tint = color,
        )
        Text(verdict + advice, style = AppType.label.copy(color = color))
    }
}

private fun conflictLabel(conflict: Conflict, s: Snapshot, lang: String): String =
    when (conflict) {
        is Conflict.Prayer -> translate(lang, "prayerOf", prayerName(conflict.anchor.prayer, lang))
        is Conflict.Event -> conflict.event.title
        is Conflict.Session ->
            s.tasks.firstOrNull { it.id == conflict.session.taskId }?.title ?: "—"
        is Conflict.ProtectedBlock ->
            "${blockLabel(conflict.block, lang)} (${translate(lang, "protected")})"
    }

@Composable
private fun Suggestions(slots: List<Interval>, zone: ZoneId, onPick: (Interval) -> Unit) {
    if (slots.isEmpty()) return
    Text(tr("suggestions"), style = AppType.micro)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEach { slot ->
            Chip(num(range(slot.start, slot.end, zone)), false, { onPick(slot) })
        }
    }
}

/** Saving over a conflict needs an explicit "schedule anyway" (AC11). */
@Composable
private fun Actions(fits: Boolean, canSave: Boolean, onSave: () -> Unit, onCancel: () -> Unit) {
    if (fits) {
        AppButton(tr("apply"), onSave, enabled = canSave)
    } else {
        AppButton(tr("cancel"), onCancel, style = ButtonStyle.SECONDARY)
        AppButton(tr("applyAnyway"), onSave, style = ButtonStyle.QUIET)
    }
}

@Composable
private fun TimeBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .heightIn(min = 64.dp)
            .card(Corner16)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = AppType.micro)
        Text(value, style = AppType.button.copy(color = AppColors.text))
    }
}
