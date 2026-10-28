package app.dailylife.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dailylife.android.Tab

val R8 = RoundedCornerShape(8.dp)
val R12 = RoundedCornerShape(12.dp)
val R16 = RoundedCornerShape(16.dp)
val R24 = RoundedCornerShape(24.dp)

fun Modifier.card(shape: RoundedCornerShape = R16, bg: Color = C.surface) =
    this.clip(shape).background(bg).border(1.dp, C.border, shape)

@Composable
fun IconBtn(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = C.primary) {
    Box(
        Modifier.size(48.dp).card().clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
}

enum class BtnStyle { PRIMARY, SECONDARY, LIGHT, QUIET }

@Composable
fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), style: BtnStyle = BtnStyle.PRIMARY, enabled: Boolean = true, icon: ImageVector? = null) {
    val (bg, fg, border) = when (style) {
        BtnStyle.PRIMARY -> Triple(C.primary, C.onPrimary, null)
        BtnStyle.SECONDARY -> Triple(C.surface, C.primary, BorderStroke(1.dp, C.border))
        BtnStyle.LIGHT -> Triple(C.surface, C.primary, null)
        BtnStyle.QUIET -> Triple(Color.Transparent, C.primary, null)
    }
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(R16)
            .background(if (enabled) bg else bg.copy(alpha = 0.45f))
            .then(if (border != null) Modifier.border(border, R16) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = T.button, color = if (enabled) fg else fg.copy(alpha = 0.6f))
    }
}

@Composable
fun Badge(text: String, bg: Color = C.highlight, fg: Color = C.dark, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.height(28.dp).clip(R8).background(bg).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.micro, color = fg) }
}

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    gap: Dp = 16.dp,
    bottomPadding: Dp = 104.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = bottomPadding),
    ) {
        item { Column(verticalArrangement = Arrangement.spacedBy(gap)) { content() } }
    }
}

@Composable
fun PageHeading(title: String, subtitle: String, onSearch: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = T.h1)
            if (onSearch != null) IconBtn(Icons.Outlined.Search, tr("search"), onSearch)
        }
        Text(subtitle, style = T.small)
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = { Spacer(Modifier.size(48.dp)) }) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconBtn(Icons.AutoMirrored.Outlined.ArrowBack, tr("back"), onBack)
        Text(title, style = T.label, color = C.secondary)
        trailing()
    }
}

@Composable
fun Checkbox(checked: Boolean) {
    Box(
        Modifier.size(24.dp).clip(R8).background(if (checked) C.primary else C.surface).border(1.5.dp, if (checked) C.primary else C.border, R8),
        contentAlignment = Alignment.Center,
    ) { if (checked) Icon(Icons.Outlined.Check, null, tint = C.onPrimary, modifier = Modifier.size(16.dp)) }
}

@Composable
fun TaskRowView(title: String, meta: String, done: Boolean, onToggle: () -> Unit, onOpen: () -> Unit, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val doneLabel = tr("complete")
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp).card().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Completion only follows an explicit tap on the checkbox.
        Box(
            Modifier.size(width = 36.dp, height = 48.dp).clickable(role = Role.Checkbox, onClick = onToggle)
                .semantics { contentDescription = doneLabel; stateDescription = if (done) "done" else "open" },
            contentAlignment = Alignment.Center,
        ) { Checkbox(done) }
        Column(Modifier.weight(1f).clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = T.body.copy(color = if (done) C.secondary else C.text, textDecoration = if (done) TextDecoration.LineThrough else null), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = T.micro)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun PrayerRow(name: String, time: String) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).clip(R16).background(C.tint).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.NightsStay, null, tint = C.primary)
        Column {
            Text(name, style = T.label, color = C.primary)
            Text("$time · ${tr("protectedTime")}", style = T.micro)
        }
    }
}

@Composable
fun BottomNav(active: Tab, onSelect: (Tab) -> Unit) {
    val items = listOf(
        Triple(Tab.TODAY, Icons.Outlined.WbSunny, "today"),
        Triple(Tab.INBOX, Icons.Outlined.Inbox, "inbox"),
        Triple(Tab.PLAN, Icons.Outlined.CalendarMonth, "plan"),
        Triple(Tab.MORE, Icons.Outlined.MoreHoriz, "more"),
    )
    Column(Modifier.fillMaxWidth().background(C.surface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 6.dp, vertical = 2.dp)) {
            items.forEach { (tab, icon, key) ->
                val selected = tab == active
                Column(
                    Modifier.weight(1f).height(64.dp).clickable(role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(56.dp, 28.dp).clip(RoundedCornerShape(999.dp)).background(if (selected) C.tint else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = if (selected) C.primary else C.secondary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(tr(key), style = T.micro, color = if (selected) C.primary else C.secondary)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) = Text(text, style = T.micro, modifier = Modifier.padding(top = 4.dp))

@Composable
fun Empty(icon: ImageVector, title: String, hint: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = C.secondary, modifier = Modifier.size(32.dp))
        Text(title, style = T.label)
        if (hint != null) Text(hint, style = T.small)
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(R12).background(C.subtle).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.weight(1f).height(36.dp).clip(R8).background(if (on) C.surface else Color.Transparent).clickable(role = Role.Tab) { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = T.label, color = if (on) C.primary else C.secondary) }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.height(44.dp).clip(R12)
            .background(if (selected) C.primary else C.surface)
            .border(1.dp, if (selected) C.primary else C.border, R12)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) C.onPrimary else C.secondary, modifier = Modifier.size(18.dp))
        Text(text, style = T.label, color = if (selected) C.onPrimary else C.secondary)
    }
}

/** A labelled row inside a details card. */
@Composable
fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = T.small)
        Text(value, style = T.label)
    }
}
