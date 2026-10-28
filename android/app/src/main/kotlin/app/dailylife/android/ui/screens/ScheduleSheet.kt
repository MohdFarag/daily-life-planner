package app.dailylife.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dailylife.android.AppViewModel
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.Btn
import app.dailylife.android.ui.BtnStyle
import app.dailylife.android.ui.C
import app.dailylife.android.ui.Chip
import app.dailylife.android.ui.IconBtn
import app.dailylife.android.ui.R12
import app.dailylife.android.ui.R16
import app.dailylife.android.ui.Segmented
import app.dailylife.android.ui.T
import app.dailylife.android.ui.card
import app.dailylife.android.ui.dayLabel
import app.dailylife.android.ui.num
import app.dailylife.android.ui.pickDate
import app.dailylife.android.ui.pickTime
import app.dailylife.android.ui.prayerName
import app.dailylife.android.ui.range
import app.dailylife.android.ui.tr
import app.dailylife.android.ui.translate
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.schedule.Conflict
import app.dailylife.core.schedule.Planner
import app.dailylife.core.schedule.RelativeScheduler
import app.dailylife.core.time.Interval
import java.time.Duration
import java.time.LocalTime
import java.util.UUID

private val DURATIONS = listOf(10, 15, 20, 30, 45, 60, 90, 120)

/**
 * Plan a session: fixed time, or relative to a prayer with explicit wording (REL01).
 * Conflicts are explained before saving; scheduling over them needs an explicit override (PLN02, AC11).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSheet(vm: AppViewModel, s: Snapshot, key: String) {
    val taskId = key.substringBefore('|')
    val editing = key.substringAfter('|', "").takeIf { it.isNotEmpty() }?.let { id -> s.sessions.firstOrNull { it.id == id } }
    val task = s.tasks.firstOrNull { it.id == taskId } ?: return
    val context = LocalContext.current
    val zone = s.prayer.zone
    val lang = s.prefs.lang
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var mode by remember { mutableStateOf("fixed") }
    var date by remember { mutableStateOf(editing?.start?.atZone(zone)?.toLocalDate() ?: vm.today()) }
    var start by remember {
        val nowRounded = LocalTime.now(zone).let { LocalTime.of(it.hour, 0).plusMinutes(((it.minute / 15) + 1) * 15L) }
        mutableStateOf(editing?.start?.atZone(zone)?.toLocalTime() ?: nowRounded)
    }
    var minutes by remember { mutableIntStateOf(editing?.let { Duration.between(it.start, it.end).toMinutes().toInt() } ?: task.estimateMin ?: 30) }
    var prayer by remember { mutableStateOf(PrayerName.ASR) }
    var anchor by remember { mutableStateOf(PrayerPoint.END) }
    var relation by remember { mutableStateOf(Relation.STARTS_AFTER) }
    var offset by remember { mutableIntStateOf(10) }
    var repeat by remember { mutableStateOf(true) }

    val anchors = vm.repo.anchors(date, s)
    val draftRule = RelativeRule("rule-preview", prayer, anchor, relation, offset, minutes, from = date, until = if (repeat) null else date)
    val slot: Interval? = if (mode == "fixed") {
        val st = date.atTime(start).atZone(zone).toInstant()
        Interval(st, st.plus(Duration.ofMinutes(minutes.toLong())))
    } else anchors.takeIf { it.isNotEmpty() }?.let { RelativeScheduler.resolve(draftRule, it) }

    val ctx = vm.repo.dayContext(s, date)
    val check = slot?.let { Planner.check(ctx, it, task.listId, ignoreSessionId = editing?.id) }
    val suggestions = if (check != null && !check.ok && mode == "fixed") {
        val dayStart = date.atTime(7, 0).atZone(zone).toInstant()
        Planner.suggest(ctx, Interval(maxOf(dayStart, vm.now()), date.atTime(22, 0).atZone(zone).toInstant().coerceAtLeast(dayStart)), Duration.ofMinutes(minutes.toLong()), task.listId)
    } else emptyList()

    fun conflictLabel(c: Conflict) = when (c) {
        is Conflict.Prayer -> translate(lang, "prayerOf", prayerName(c.anchor.prayer, lang))
        is Conflict.Event -> c.event.title
        is Conflict.Session -> s.tasks.firstOrNull { it.id == c.session.taskId }?.title ?: "—"
        is Conflict.ProtectedBlock -> "${c.block.name} (${translate(lang, "protected")})"
    }

    fun apply() {
        if (mode == "fixed" && slot != null) vm.saveFixedSession(task.id, slot.start, slot.end, editing?.id)
        else vm.saveRule(task.id, draftRule.copy(id = "rule-" + UUID.randomUUID()))
        vm.scheduleTaskId = null
    }

    ModalBottomSheet(onDismissRequest = { vm.scheduleTaskId = null }, sheetState = sheet, containerColor = C.canvas, dragHandle = {
        Box(Modifier.padding(top = 12.dp).size(40.dp, 4.dp).clip(R12).background(C.border))
    }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("planSession"), style = T.h2)
                IconBtn(Icons.Outlined.Close, tr("close"), { vm.scheduleTaskId = null })
            }
            Text("${task.title}${task.estimateMin?.let { " · " + translate(lang, "minutes", it) } ?: ""}", style = T.small)
            if (editing == null) Segmented(listOf("fixed" to tr("fixedTime"), "relative" to tr("relativeToPrayer")), mode, { mode = it }, Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).card().clickable { pickDate(context, date, vm.today()) { date = it } }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = C.primary)
                Text(dayLabel(date, lang), style = T.label)
            }

            if (mode == "fixed") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeBox(tr("startTime"), num(start.toString()), Modifier.weight(1f)) { pickTime(context, start) { start = it } }
                    TimeBox(tr("duration"), translate(lang, "minutes", minutes), Modifier.weight(1f)) { minutes = DURATIONS[(DURATIONS.indexOf(minutes) + 1) % DURATIONS.size] }
                }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrayerName.entries.forEach { p -> Chip(prayerName(p, lang), prayer == p, { prayer = p }) }
                }
                Segmented(listOf(Relation.STARTS_AFTER.name to tr("startsAfter"), Relation.STARTS_BEFORE.name to tr("startsBefore"), Relation.ENDS_BEFORE.name to tr("endsBefore")),
                    relation.name, { relation = Relation.valueOf(it) }, Modifier.fillMaxWidth())
                Segmented(listOf(PrayerPoint.ADHAN.name to tr("adhan"), PrayerPoint.IQAMA.name to tr("iqama"), PrayerPoint.END.name to tr("periodEnd")),
                    anchor.name, { anchor = PrayerPoint.valueOf(it) }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeBox(tr("offset"), translate(lang, "minutes", offset), Modifier.weight(1f)) { offset = listOf(0, 5, 10, 15, 20, 30, 45, 60).let { it[(it.indexOf(offset) + 1) % it.size] } }
                    TimeBox(tr("duration"), translate(lang, "minutes", minutes), Modifier.weight(1f)) { minutes = DURATIONS[(DURATIONS.indexOf(minutes) + 1) % DURATIONS.size] }
                }
                Chip(tr("repeatDaily"), repeat, { repeat = !repeat })
            }

            if (slot != null) Column(Modifier.fillMaxWidth().clip(R16).background(C.tint).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(num(range(slot.start, slot.end, zone)), style = T.h2.copy(color = C.primary))
                Text(listOfNotNull(dayLabel(date, lang), check?.insideBlock?.name, if (mode == "relative" && repeat) tr("every") else null).joinToString(" · "), style = T.micro.copy(color = C.primary))
            }

            if (check != null) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (check.ok) Icons.Outlined.Check else Icons.Outlined.WarningAmber, null, tint = if (check.ok) C.primary else C.warningText)
                Text(
                    when {
                        !check.ok -> tr("conflict", check.conflicts.joinToString(", ") { conflictLabel(it) })
                        check.insideBlock != null -> tr("fitsBlock", check.insideBlock!!.name)
                        else -> tr("free")
                    } + check.advice.joinToString("") { " ${it.message}." },
                    style = T.label.copy(color = if (check.ok) C.primary else C.warningText),
                )
            }
            if (suggestions.isNotEmpty()) {
                Text(tr("suggestions"), style = T.micro)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { sug ->
                        Chip(num(range(sug.start, sug.end, zone)), false, { start = sug.start.atZone(zone).toLocalTime() })
                    }
                }
            }

            if (check == null || check.ok) Btn(tr("apply"), ::apply, enabled = slot != null)
            else {
                Btn(tr("cancel"), { vm.scheduleTaskId = null }, BtnStyle.SECONDARY)
                Btn(tr("applyAnyway"), ::apply, BtnStyle.QUIET)
            }
            Text(tr("multiSession"), style = T.micro)
        }
    }
}

@Composable
private fun TimeBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 64.dp).card(R16).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
        Text(label, style = T.micro)
        Text(value, style = T.button.copy(color = C.text))
    }
}
