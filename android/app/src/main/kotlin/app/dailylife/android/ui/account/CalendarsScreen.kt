package app.dailylife.android.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.CalendarEntry
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Chip
import app.dailylife.android.ui.components.Field
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.SectionLabel
import app.dailylife.android.ui.components.TopBar
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel

/** Which Google calendars appear in the plan, where new events go, and new calendars (CAL02). */
@Composable
fun CalendarsScreen(vm: AppViewModel, s: Snapshot) {
    val settings = s.calendars
    ScreenColumn {
        TopBar(tr("calendars"), { vm.navigator.back() })
        if (settings == null) {
            Text(tr("googleNotConnected"), style = AppType.small)
            return@ScreenColumn
        }
        Text(tr("calendarsExplain"), style = AppType.small)
        settings.list
            .filter { it.appRole == null }
            .forEach { calendar ->
                CalendarRow(
                    calendar,
                    isDefault = calendar.id == settings.defaultCalendarId,
                    onVisible = { vm.google.setVisible(calendar.id, it) },
                    onDefault = { vm.google.setDefault(calendar.id) },
                )
            }
        SectionLabel(tr("appCalendars"))
        settings.list
            .filter { it.appRole != null }
            .forEach {
                Text(
                    "${it.summary} · ${tr(if (it.appRole == "sessions") "appCalendarSessions" else "appCalendarBlocks")}",
                    style = AppType.small,
                )
            }
        NewCalendar(vm)
    }
}

@Composable
private fun CalendarRow(
    calendar: CalendarEntry,
    isDefault: Boolean,
    onVisible: (Boolean) -> Unit,
    onDefault: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(calendar.summary, style = AppType.label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(tr("showInPlan"), calendar.visible, { onVisible(!calendar.visible) })
            if (calendar.writable) Chip(tr("defaultCalendar"), isDefault, onDefault)
            if (!calendar.writable) Text(tr("readOnlyCalendar"), style = AppType.micro)
        }
    }
}

@Composable
private fun NewCalendar(vm: AppViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    SectionLabel(tr("newCalendar"))
    Field(name, tr("calendarName")) { name = it }
    AppButton(
        tr("createCalendar"),
        {
            vm.google.createCalendar(name)
            name = ""
        },
        enabled = name.isNotBlank() && !vm.google.busy,
        style = ButtonStyle.SECONDARY,
    )
}
