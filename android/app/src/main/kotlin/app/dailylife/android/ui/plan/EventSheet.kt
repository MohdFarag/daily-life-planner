package app.dailylife.android.ui.plan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.AppSheet
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Chip
import app.dailylife.android.ui.components.Field
import app.dailylife.android.ui.components.SectionLabel
import app.dailylife.android.ui.components.Segmented
import app.dailylife.android.ui.components.SheetHeader
import app.dailylife.android.ui.components.pickTime
import app.dailylife.android.ui.format.anchorKey
import app.dailylife.android.ui.format.blockLabel
import app.dailylife.android.ui.format.prayerName
import app.dailylife.android.ui.format.range
import app.dailylife.android.ui.format.relationKey
import app.dailylife.android.ui.i18n.LocalLang
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.android.viewmodel.EventTarget
import app.dailylife.core.model.CalendarEvent
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation

/** Creates or edits a standalone event (CAL01), at a fixed time or relative to a prayer (REL01). */
@Composable
fun EventSheet(vm: AppViewModel, s: Snapshot, target: EventTarget) {
    val existing = target.eventId?.let { id -> s.events.firstOrNull { it.id == id } }
    val close = { vm.editingEvent = null }
    if (existing?.readOnly == true) {
        AppSheet(onDismiss = close) { ReadOnlyEvent(existing, s, close) }
        return
    }
    val defaultCalendar = s.calendars?.defaultCalendarId.orEmpty()
    var draft by
        remember(target) {
            mutableStateOf(
                existing?.let { EventDraft.from(it, s.prayer.zone) }
                    ?: EventDraft("", target.date, calendarId = defaultCalendar)
            )
        }
    var untimed by remember(target) { mutableStateOf(false) }

    AppSheet(onDismiss = close) {
        SheetHeader(tr(if (existing == null) "newEvent" else "editEvent"), onClose = close)
        Field(draft.title, tr("eventTitle")) { draft = draft.copy(title = it) }
        DateField(draft.date, minDate = vm.today()) { draft = draft.copy(date = it) }
        Chip(tr("allDay"), draft.allDay, { draft = draft.copy(allDay = !draft.allDay) })
        if (!draft.allDay) Timing(draft) { draft = it }
        CalendarChoice(s, draft) { draft = draft.copy(calendarId = it) }
        BlockChoice(s, draft) { draft = draft.copy(blockId = it) }
        Field(draft.location, tr("location")) { draft = draft.copy(location = it) }
        if (untimed)
            Text(tr("noPrayerTimes"), style = AppType.small.copy(color = AppColors.warningText))
        AppButton(
            tr("save"),
            { if (vm.events.save(draft, existing?.id)) close() else untimed = true },
            enabled = draft.canSave,
        )
        if (existing != null)
            AppButton(
                tr("deleteEvent"),
                {
                    vm.events.delete(existing.id)
                    close()
                },
                style = ButtonStyle.QUIET,
            )
    }
}

@Composable
private fun Timing(draft: EventDraft, onChange: (EventDraft) -> Unit) {
    val context = LocalContext.current
    val lang = LocalLang.current
    Segmented(
        listOf(
            ScheduleMode.FIXED.name to tr("fixedTime"),
            ScheduleMode.RELATIVE.name to tr("relativeToPrayer"),
        ),
        draft.mode.name,
        { onChange(draft.copy(mode = ScheduleMode.valueOf(it))) },
        Modifier.fillMaxWidth(),
    )
    if (draft.mode == ScheduleMode.RELATIVE) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrayerName.entries.forEach { p ->
                Chip(prayerName(p, lang), draft.prayer == p, { onChange(draft.copy(prayer = p)) })
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
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (draft.mode == ScheduleMode.FIXED)
            TimeBox(tr("startTime"), num(draft.start.toString()), Modifier.weight(1f)) {
                pickTime(context, draft.start) { onChange(draft.copy(start = it)) }
            }
        else
            TimeBox(tr("offset"), tr("minutes", draft.offsetMinutes), Modifier.weight(1f)) {
                onChange(draft.nextOffset())
            }
        TimeBox(tr("duration"), tr("minutes", draft.minutes), Modifier.weight(1f)) {
            onChange(draft.nextDuration())
        }
    }
}

/** Writable calendars that are shown; without Google, events stay on this device. */
@Composable
private fun CalendarChoice(s: Snapshot, draft: EventDraft, onPick: (String) -> Unit) {
    val writable =
        s.calendars?.list.orEmpty().filter { it.writable && it.visible && it.appRole == null }
    if (writable.isEmpty()) return
    SectionLabel(tr("calendar"))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        writable.forEach { c -> Chip(c.summary, draft.calendarId == c.id, { onPick(c.id) }) }
    }
}

/** Optionally ties the event to a time block, without creating a task (CAL03). */
@Composable
private fun BlockChoice(s: Snapshot, draft: EventDraft, onPick: (String?) -> Unit) {
    if (s.blocks.isEmpty()) return
    val lang = LocalLang.current
    SectionLabel(tr("inBlock"))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(tr("none"), draft.blockId == null, { onPick(null) })
        s.blocks.forEach { b -> Chip(blockLabel(b, lang), draft.blockId == b.id, { onPick(b.id) }) }
    }
}

@Composable
private fun ReadOnlyEvent(event: CalendarEvent, s: Snapshot, onClose: () -> Unit) {
    val calendar = s.calendars?.list?.firstOrNull { it.id == event.calendarId }?.summary
    SheetHeader(event.title, onClose = onClose)
    Text(num(range(event.start, event.end, s.prayer.zone)), style = AppType.label)
    listOfNotNull(calendar, event.location, tr("readOnlyCalendar")).forEach {
        Text(it, style = AppType.small)
    }
}
