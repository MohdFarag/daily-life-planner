package app.dailylife.android.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.dailylife.android.AppViewModel
import app.dailylife.android.Screen
import app.dailylife.android.data.CITIES
import app.dailylife.android.data.Snapshot
import app.dailylife.android.data.settings
import app.dailylife.android.ui.Btn
import app.dailylife.android.ui.BtnStyle
import app.dailylife.android.ui.C
import app.dailylife.android.ui.Chip
import app.dailylife.android.ui.DetailRow
import app.dailylife.android.ui.Empty
import app.dailylife.android.ui.PageHeading
import app.dailylife.android.ui.R12
import app.dailylife.android.ui.ScreenColumn
import app.dailylife.android.ui.SectionLabel
import app.dailylife.android.ui.Segmented
import app.dailylife.android.ui.T
import app.dailylife.android.ui.TaskRowView
import app.dailylife.android.ui.TopBar
import app.dailylife.android.ui.card
import app.dailylife.android.ui.kindIcon
import app.dailylife.android.ui.num
import app.dailylife.android.ui.pickTime
import app.dailylife.android.ui.prayerName
import app.dailylife.android.ui.range
import app.dailylife.android.ui.shortDate
import app.dailylife.android.ui.tr
import app.dailylife.core.model.BlockPolicy
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureState
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.TaskStatus
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.prayer.EndRule
import app.dailylife.core.prayer.IqamaRule
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Madhab
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.UUID

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).card().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(R12).background(C.tint), contentAlignment = Alignment.Center) { Icon(icon, null, tint = C.primary) }
        Column(Modifier.weight(1f)) {
            Text(title, style = T.body)
            Text(subtitle, style = T.micro)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.secondary)
    }
}

@Composable
fun MoreScreen(vm: AppViewModel, s: Snapshot) {
    val context = LocalContext.current
    ScreenColumn {
        PageHeading(tr("more"), tr("moreSubtitle"), null)
        SectionLabel(tr("yourSpace"))
        NavRow(Icons.Outlined.Checklist, tr("allTasks"), tr("openCount", s.tasks.count { it.status != TaskStatus.COMPLETED })) { vm.go(Screen.Tasks) }
        NavRow(Icons.Outlined.FolderOpen, tr("collections"), tr("collectionsSub")) { vm.go(Screen.Collections) }
        SectionLabel(tr("settings"))
        NavRow(Icons.Outlined.NightsStay, tr("prayerTimes"), s.prayer.label.ifEmpty { "%.2f, %.2f".format(s.prayer.latitude, s.prayer.longitude) }) { vm.go(Screen.PrayerSettings) }
        NavRow(Icons.Outlined.ViewTimeline, tr("timeBlocks"), tr("timeBlocksSub")) { vm.go(Screen.Blocks) }
        NavRow(Icons.AutoMirrored.Outlined.List, tr("lists"), s.lists.joinToString { it.name }) { vm.go(Screen.Lists) }
        NavRow(Icons.Outlined.Tune, tr("preferences"), tr("preferencesSub")) { vm.go(Screen.Preferences) }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(tr("exportData"), style = T.label, color = C.primary, modifier = Modifier.clickable {
                // Documented, restorable JSON export shared through the system sheet (SEC02).
                val f = File(vm.repo.mediaDir, "daily-life-planner-export.json").apply { writeText(vm.repo.exportJson()) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
            }.padding(vertical = 12.dp))
            Text(tr("trash"), style = T.label, color = C.primary, modifier = Modifier.clickable { vm.go(Screen.Trash) }.padding(vertical = 12.dp))
        }
    }
}

@Composable
fun TasksScreen(vm: AppViewModel, s: Snapshot) {
    var filter by remember { mutableStateOf("all") }
    var newTitle by remember { mutableStateOf("") }
    val tasks = s.tasks.filter { filter == "all" || it.listId == filter }.sortedWith(compareBy({ it.status == TaskStatus.COMPLETED }, { !it.important }, { it.deadline ?: java.time.LocalDate.MAX }))
    ScreenColumn {
        TopBar(tr("allTasks"), { vm.back() })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("all"), filter == "all", { filter = "all" })
            s.lists.forEach { l -> Chip(l.name, filter == l.id, { filter = l.id }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field(newTitle, tr("newTask")) { newTitle = it } }
            Btn("+", { vm.addTask(newTitle.trim(), if (filter == "all") s.lists.first().id else filter); newTitle = "" }, enabled = newTitle.isNotBlank(), modifier = Modifier.size(52.dp))
        }
        tasks.forEach { t ->
            val list = s.lists.firstOrNull { it.id == t.listId }?.name.orEmpty()
            val meta = listOfNotNull(list, t.deadline?.let { "${tr("deadline")} ${shortDate(it, vm.lang)}" }, if (t.important) "★" else null, t.tags.joinToString(" ") { "#$it" }.ifEmpty { null }).joinToString(" · ")
            TaskRowView(t.title, meta, t.status == TaskStatus.COMPLETED, { vm.toggleDone(t) }, { vm.go(Screen.TaskDetail(t.id)) })
        }
    }
}

@Composable
private fun ItemCard(item: CaptureItem, actions: List<Pair<String, () -> Unit>>, extra: String? = null) {
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(kindIcon(item.kind), null, tint = C.primary)
            Column(Modifier.weight(1f)) {
                Text(item.title, style = T.body)
                if (item.body.isNotBlank()) Text(item.body.lineSequence().first(), style = T.small)
                if (extra != null) Text(extra, style = T.micro)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)) {
            actions.forEach { (label, action) -> Text(label, style = T.label, color = C.primary, modifier = Modifier.clickable(onClick = action).padding(8.dp)) }
        }
    }
}

@Composable
fun CollectionsScreen(vm: AppViewModel, s: Snapshot) {
    val groups = listOf(CaptureState.REFERENCE to tr("references"), CaptureState.SOMEDAY to tr("someday"), CaptureState.WAITING to tr("waitingFor"))
    ScreenColumn {
        TopBar(tr("collections"), { vm.back() })
        if (s.items.none { i -> groups.any { it.first == i.state } }) Empty(Icons.Outlined.FolderOpen, tr("emptyCollection"))
        groups.forEach { (state, label) ->
            val items = s.items.filter { it.state == state }
            if (items.isEmpty()) return@forEach
            SectionLabel("$label · ${num(items.size)}")
            items.forEach { i ->
                val extra = i.waiting?.let { w -> "${w.who}${w.followUpOn?.let { " · ${tr("followUpOn")} ${shortDate(it, vm.lang)}" } ?: ""}" }
                    ?: i.reviewOn?.let { "${tr("reviewOn")} ${shortDate(it, vm.lang)}" }
                ItemCard(i, listOf(tr("returnToInbox") to { vm.returnToInbox(i); Unit }, tr("moveToTrash") to { vm.move(i, CaptureState.TRASHED) }), extra)
            }
        }
    }
}

@Composable
fun TrashScreen(vm: AppViewModel, s: Snapshot) {
    val items = s.items.filter { it.state == CaptureState.TRASHED }
    ScreenColumn {
        TopBar(tr("trash"), { vm.back() })
        if (items.isEmpty()) Empty(Icons.Outlined.DeleteOutline, tr("trashEmpty"))
        items.forEach { i ->
            ItemCard(i, listOf(tr("restore") to { vm.restore(i); Unit }, tr("deleteForever") to { vm.deleteForever(listOf(i.id)); Unit }),
                i.purgeAt?.let { tr("purgeOn", shortDate(it.atZone(s.prayer.zone).toLocalDate(), vm.lang)) })
        }
        if (items.isNotEmpty()) Btn(tr("emptyTrash"), { vm.deleteForever(items.map { it.id }) }, style = BtnStyle.SECONDARY)
    }
}

@Composable
fun PrayerSettingsScreen(vm: AppViewModel, s: Snapshot) {
    val p = s.prayer
    val today = vm.today()
    val anchors = vm.repo.anchors(today, s)
    val error = vm.repo.anchors(today, s).isEmpty()
    fun iqamaMin(n: PrayerName) = (p.iqama[n] as? IqamaRule.AfterAdhan)?.minutes ?: 15
    fun endMin(n: PrayerName) = (p.end[n] as? EndRule.AfterIqama)?.minutes ?: 15
    val steps = listOf(0, 5, 10, 15, 20, 25, 30, 45)
    ScreenColumn {
        TopBar(tr("prayerTimes"), { vm.back() })
        SectionLabel(tr("city"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CITIES.forEach { c ->
                Chip(if (vm.lang == "ar") c.nameAr else c.name, p.label == c.name, {
                    vm.setPrayer(c.settings().copy(iqama = p.iqama, end = p.end, adjustmentsMin = p.adjustmentsMin, madhab = p.madhab, fridayExtraMin = p.fridayExtraMin))
                })
            }
        }
        SectionLabel(tr("method"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalculationMethod.entries.filter { it != CalculationMethod.OTHER }.forEach { m ->
                Chip(m.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, p.method == m, { vm.setPrayer(p.copy(method = m)) })
            }
        }
        Row(Modifier.fillMaxWidth().card().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("asrMethod"), style = T.body, modifier = Modifier.weight(1f))
            Switch(p.madhab == Madhab.HANAFI, { vm.setPrayer(p.copy(madhab = if (it) Madhab.HANAFI else Madhab.SHAFI)) }, colors = SwitchDefaults.colors(checkedTrackColor = C.primary))
        }
        Row(Modifier.fillMaxWidth().card().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("protectPrayers"), style = T.body, modifier = Modifier.weight(1f))
            Switch(s.prefs.protectPrayers, { vm.setPrefs(s.prefs.copy(protectPrayers = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = C.primary))
        }
        // Per-prayer adhan, iqama and end of period (PRY01/PRY02). Tapping a value steps through common offsets.
        PrayerName.entries.forEach { n ->
            val a = anchors.firstOrNull { it.prayer == n }
            Column(Modifier.fillMaxWidth().clip(R12).background(C.tint).padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(prayerName(n, vm.lang), style = T.label, color = C.primary)
                    if (a != null) Text(num(range(a.adhan, a.end, p.zone)), style = T.micro)
                }
                DetailRow(tr("adjust"), num(p.adjustmentsMin[n] ?: 0)) {
                    val next = ((p.adjustmentsMin[n] ?: 0) + 1).let { if (it > 5) -5 else it }
                    vm.setPrayer(p.copy(adjustmentsMin = p.adjustmentsMin + (n to next)))
                }
                DetailRow(tr("iqamaAfter"), num(iqamaMin(n))) {
                    vm.setPrayer(p.copy(iqama = p.iqama + (n to IqamaRule.AfterAdhan(steps[(steps.indexOf(iqamaMin(n)) + 1) % steps.size]))))
                }
                DetailRow(tr("endAfter"), num(endMin(n))) {
                    vm.setPrayer(p.copy(end = p.end + (n to EndRule.AfterIqama(steps[(steps.indexOf(endMin(n)) + 1) % steps.size]))))
                }
            }
        }
        DetailRow(tr("fridayExtra"), num(p.fridayExtraMin)) { vm.setPrayer(p.copy(fridayExtraMin = (p.fridayExtraMin + 15) % 75)) }
        if (error) Text("⚠", style = T.label, color = C.warningText)
        Text(tr("methodNote") + " · ${p.zone.id}", style = T.micro)
    }
}

@Composable
fun BlocksScreen(vm: AppViewModel, s: Snapshot) {
    val context = LocalContext.current
    ScreenColumn {
        TopBar(tr("timeBlocks"), { vm.back() })
        s.blocks.forEach { b ->
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(b.name, { vm.saveBlock(b.copy(name = it)) }, textStyle = T.body, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Chip("${tr("from")} ${num(b.start.toString())}", false, { pickTime(context, b.start) { vm.saveBlock(b.copy(start = it)) } })
                    Chip("${tr("to")} ${num(b.end.toString())}", false, { pickTime(context, b.end) { if (it.isAfter(b.start)) vm.saveBlock(b.copy(end = it)) } })
                }
                Segmented(listOf(BlockPolicy.GUIDING.name to tr("guiding"), BlockPolicy.PROTECTED.name to tr("protected")), b.policy.name, { vm.saveBlock(b.copy(policy = BlockPolicy.valueOf(it))) }, Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        Chip(d.getDisplayName(TextStyle.SHORT, app.dailylife.android.ui.locale(vm.lang)), d in b.days, {
                            vm.saveBlock(b.copy(days = if (d in b.days) b.days - d else b.days + d))
                        })
                    }
                }
                Text(tr("allowedLists"), style = T.micro)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.lists.forEach { l ->
                        Chip(l.name, l.id in b.allowedListIds, { vm.saveBlock(b.copy(allowedListIds = if (l.id in b.allowedListIds) b.allowedListIds - l.id else b.allowedListIds + l.id)) })
                    }
                }
                Text(tr("delete"), style = T.label, color = C.danger, modifier = Modifier.clickable { vm.deleteBlock(b) }.padding(vertical = 8.dp))
            }
        }
        val newName = tr("timeBlocks")
        Btn(tr("addBlock"), {
            vm.saveBlock(TimeBlock("block-" + UUID.randomUUID(), newName, LocalTime.of(14, 0), LocalTime.of(16, 0), DayOfWeek.entries.toSet(), BlockPolicy.GUIDING))
        }, style = BtnStyle.SECONDARY)
    }
}

@Composable
fun ListsScreen(vm: AppViewModel, s: Snapshot) {
    var name by remember { mutableStateOf("") }
    ScreenColumn {
        TopBar(tr("lists"), { vm.back() })
        s.lists.forEach { l ->
            DetailRow(l.name, tr("openCount", s.tasks.count { it.listId == l.id && it.status != TaskStatus.COMPLETED }))
        }
        Field(name, tr("newList")) { name = it }
        Btn(tr("addList"), { vm.addList(name.trim()); name = "" }, enabled = name.isNotBlank())
    }
}

@Composable
fun PreferencesScreen(vm: AppViewModel, s: Snapshot) {
    val prefs = s.prefs
    ScreenColumn {
        TopBar(tr("preferences"), { vm.back() })
        SectionLabel(tr("language"))
        Segmented(listOf("en" to "English", "ar" to "العربية"), prefs.lang, { vm.setPrefs(prefs.copy(lang = it)) }, Modifier.fillMaxWidth())
        DetailRow(tr("reminderBefore"), num(prefs.reminderMinutes)) {
            val steps = listOf(0, 5, 10, 15, 30)
            vm.setPrefs(prefs.copy(reminderMinutes = steps[(steps.indexOf(prefs.reminderMinutes) + 1) % steps.size]))
        }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel, s: Snapshot) {
    var q by remember { mutableStateOf("") }
    val query = q.trim().lowercase()
    val tasks = if (query.isEmpty()) emptyList() else s.tasks.filter { (it.title + " " + it.notes + " " + it.tags.joinToString(" ")).lowercase().contains(query) }
    val items = if (query.isEmpty()) emptyList() else s.items.filter {
        it.state != CaptureState.TRASHED && it.state != CaptureState.PROCESSED &&
            (it.title + " " + it.body + " " + (it.url ?: "") + " " + it.entries.joinToString(" ") { e -> e.text }).lowercase().contains(query)
    }
    ScreenColumn {
        TopBar(tr("search"), { vm.back() })
        Field(q, tr("searchHint")) { q = it }
        tasks.forEach { t -> TaskRowView(t.title, s.lists.firstOrNull { it.id == t.listId }?.name.orEmpty(), t.status == TaskStatus.COMPLETED, { vm.toggleDone(t) }, { vm.go(Screen.TaskDetail(t.id)) }) }
        items.forEach { i -> app.dailylife.android.ui.screens.InboxCard(i, vm.lang) { if (i.state == CaptureState.INBOX || i.state == CaptureState.DOING_NOW) vm.go(Screen.Process(i.id)) else vm.go(Screen.Collections) } }
        if (query.isNotEmpty() && tasks.isEmpty() && items.isEmpty()) Empty(Icons.Outlined.Search, tr("noResults"))
    }
}
