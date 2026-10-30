package app.dailylife.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dailylife.android.AppViewModel
import app.dailylife.android.Screen
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.blockLabel
import app.dailylife.android.ui.listName
import app.dailylife.android.ui.C
import app.dailylife.android.ui.Chip
import app.dailylife.android.ui.PageHeading
import app.dailylife.android.ui.R12
import app.dailylife.android.ui.R8
import app.dailylife.android.ui.ScreenColumn
import app.dailylife.android.ui.Segmented
import app.dailylife.android.ui.T
import app.dailylife.android.ui.TaskRowView
import app.dailylife.android.ui.card
import app.dailylife.android.ui.hm
import app.dailylife.android.ui.locale
import app.dailylife.android.ui.localizeDigits
import app.dailylife.android.ui.num
import app.dailylife.android.ui.prayerName
import app.dailylife.android.ui.range
import app.dailylife.android.ui.tr
import app.dailylife.android.ui.translate
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle

private const val HOUR_DP = 74.4f
private const val START_HOUR = 5
private const val END_HOUR = 23

private data class Block(val key: String, val label: String, val start: Instant, val end: Instant, val kind: String, val done: Boolean = false, val taskId: String? = null, val review: Boolean = false)

@Composable
fun PlanScreen(vm: AppViewModel, s: Snapshot) {
    val today = vm.today()
    val zone = s.prayer.zone
    val lang = s.prefs.lang
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    var view by rememberSaveable { mutableStateOf("day") }
    val date = LocalDate.parse(selected)
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    val week = (0..6).map { monday.plusDays(it.toLong()) }

    fun blocksFor(d: LocalDate): List<Block> {
        val ctx = vm.repo.dayContext(s, d)
        val tasks = s.tasks.associateBy { it.id }
        return buildList {
            ctx.prayers.forEach { add(Block("p${it.prayer}", translate(lang, "prayerOf", prayerName(it.prayer, lang)), it.adhan, it.end, "prayer")) }
            ctx.events.forEach { add(Block("e${it.id}", it.title, it.start, it.end, "event")) }
            ctx.sessions.map { it.first }.filter { it.state != SessionState.CANCELED }.forEach { se ->
                val t = tasks[se.taskId] ?: return@forEach
                add(Block("s${se.id}", t.title, se.start, se.end, "task", t.status == TaskStatus.COMPLETED, t.id, se.state == SessionState.NEEDS_REVIEW))
            }
        }.sortedBy { it.start }
    }

    ScreenColumn {
        PageHeading(tr("plan"), date.month.getDisplayName(TextStyle.FULL_STANDALONE, locale(lang)) + " " + localizeDigits("${date.year}", lang)) { vm.go(Screen.Search) }

        Row(Modifier.fillMaxWidth()) {
            week.forEach { d ->
                val sel = d == date
                val hasWork = s.sessions.any { it.start.atZone(zone).toLocalDate() == d && it.state != SessionState.CANCELED }
                Column(Modifier.weight(1f).height(60.dp).clickable { selected = d.toString() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale(lang)), style = T.micro)
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (sel) C.primary else if (d == today) C.tint else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(localizeDigits("${d.dayOfMonth}", lang), style = T.label, color = if (sel) C.onPrimary else if (d == today) C.primary else C.text)
                        if (hasWork && !sel) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(4.dp).clip(CircleShape).background(C.secondary))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf("day" to tr("day"), "week" to tr("week")), view, { view = it }, Modifier.width(170.dp))
            Chip(tr("agenda"), view == "agenda", { view = "agenda" })
        }

        when (view) {
            "day" -> DayTimeline(vm, s, date, blocksFor(date))
            "agenda" -> Agenda(vm, s, blocksFor(date))
            else -> week.forEach { d ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.FULL, locale(lang)) + " " + localizeDigits("${d.dayOfMonth}", lang),
                        style = T.label.copy(color = if (d == today) C.primary else C.text), modifier = Modifier.clickable { selected = d.toString(); view = "day" })
                    val items = blocksFor(d).filter { it.kind != "prayer" }
                    if (items.isEmpty()) Text(tr("emptyDay"), style = T.micro) else Agenda(vm, s, items)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(C.primary to tr("tasks"), C.tint to tr("prayers"), C.warningText to tr("events")).forEach { (c, l) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c).border(1.dp, C.primary.copy(alpha = 0.3f), CircleShape))
                    Text(l, style = T.micro)
                }
            }
        }

        val unplanned = s.tasks.filter { t -> t.status != TaskStatus.COMPLETED && s.sessions.none { it.taskId == t.id && it.end.isAfter(vm.now()) && it.state != SessionState.CANCELED } && s.rules.none { it.taskId == t.id } }
        if (unplanned.isNotEmpty()) {
            Text(tr("notPlanned"), style = T.h3)
            unplanned.forEach { t ->
                val list = listName(s, t.listId).orEmpty()
                TaskRowView(t.title, listOfNotNull(t.estimateMin?.let { "${num(it)} ${tr("min")}" }, list).joinToString(" · "), false, { vm.toggleDone(t) }, { vm.go(Screen.TaskDetail(t.id)) }) {
                    Text(tr("planSession"), style = T.label, color = C.primary, modifier = Modifier.clip(R8).clickable { vm.scheduleTaskId = t.id }.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DayTimeline(vm: AppViewModel, s: Snapshot, date: LocalDate, blocks: List<Block>) {
    val zone = s.prayer.zone
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    fun y(i: Instant): Float {
        val t = i.atZone(zone)
        val minutes = if (t.toLocalDate().isAfter(date)) 24 * 60f else t.hour * 60f + t.minute
        return ((minutes - START_HOUR * 60) / 60f * HOUR_DP).coerceIn(0f, (END_HOUR - START_HOUR) * HOUR_DP)
    }
    LaunchedEffect(date) { scroll.scrollTo(with(density) { ((9 - START_HOUR) * HOUR_DP).dp.roundToPx() }) }

    Box(Modifier.fillMaxWidth().height(372.dp).clip(R12).verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().height(((END_HOUR - START_HOUR) * HOUR_DP + 12).dp)) {
            // Time blocks as a light background beneath sessions (BLK01).
            vm.repo.dayContext(s, date).activeBlocks.forEach { b ->
                val top = ((b.start.toSecondOfDay() / 60f - START_HOUR * 60) / 60f * HOUR_DP).coerceAtLeast(0f)
                val bottom = ((b.end.toSecondOfDay() / 60f - START_HOUR * 60) / 60f * HOUR_DP)
                Box(
                    Modifier.offset(x = 58.dp, y = top.dp).fillMaxWidth().padding(end = 60.dp).height((bottom - top).dp)
                        .clip(R12).background(Color(b.color).copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text(blockLabel(b, s.prefs.lang).uppercase() + if (b.policy == app.dailylife.core.model.BlockPolicy.PROTECTED) " · ${tr("protected")}" else "", style = T.micro) }
            }
            (START_HOUR..END_HOUR).forEach { h ->
                val top = ((h - START_HOUR) * HOUR_DP).dp
                Text(num("%02d:00".format(h)), style = T.micro, modifier = Modifier.offset(y = top - 9.dp).width(48.dp))
                Box(Modifier.offset(x = 58.dp, y = top).fillMaxWidth().height(1.dp).background(C.border))
            }
            blocks.forEach { b ->
                val top = y(b.start)
                val h = (y(b.end) - top - 2).coerceAtLeast(22f)
                val (bg, fg) = when (b.kind) {
                    "task" -> if (b.done) C.tint to C.secondary else if (b.review) C.warning to C.warningText else C.primary to C.onPrimary
                    "prayer" -> C.tint to C.primary
                    else -> C.warning to C.warningText
                }
                Column(
                    Modifier.offset(x = 62.dp, y = (top + 1).dp).fillMaxWidth().padding(end = 62.dp).height(h.dp).clip(R8).background(bg)
                        .then(if (b.taskId != null) Modifier.clickable { vm.go(Screen.TaskDetail(b.taskId)) } else Modifier)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val time = num(range(b.start, b.end, zone))
                    if (b.kind == "task" && h > 44) {
                        Text(b.label, style = T.label.copy(color = fg, textDecoration = if (b.done) TextDecoration.LineThrough else null), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(time, style = T.micro.copy(color = fg))
                    } else Text("$time · ${b.label}", style = T.micro.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (date == vm.today()) {
                val top = y(Instant.now())
                Box(Modifier.offset(x = 54.dp, y = top.dp).fillMaxWidth().height(1.5.dp).background(C.now))
            }
        }
    }
}

@Composable
private fun Agenda(vm: AppViewModel, s: Snapshot, blocks: List<Block>) {
    val zone = s.prayer.zone
    if (blocks.isEmpty()) { Text(tr("emptyDay"), style = T.small); return }
    Column(Modifier.fillMaxWidth().card()) {
        blocks.forEachIndexed { i, b ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (b.taskId != null) Modifier.clickable { vm.go(Screen.TaskDetail(b.taskId)) } else Modifier).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(num(b.start.hm(zone)), style = T.micro, modifier = Modifier.width(44.dp))
                Box(Modifier.width(4.dp).height(32.dp).clip(R8).background(when (b.kind) { "task" -> C.primary; "prayer" -> C.tint; else -> C.warningText }))
                Column {
                    Text(b.label, style = T.label.copy(color = if (b.done) C.secondary else C.text, textDecoration = if (b.done) TextDecoration.LineThrough else null))
                    Text(num(range(b.start, b.end, zone)) + if (b.review) " · ${tr("needsReview")}" else "", style = T.micro)
                }
            }
        }
    }
}
