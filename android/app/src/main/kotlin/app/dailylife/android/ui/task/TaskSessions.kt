package app.dailylife.android.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dailylife.android.ui.components.AppIconButton
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.format.prayerName
import app.dailylife.android.ui.format.range
import app.dailylife.android.ui.format.relationKey
import app.dailylife.android.ui.format.shortDate
import app.dailylife.android.ui.i18n.LocalLang
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.android.viewmodel.ScheduleTarget
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TimingRule
import java.time.ZoneId

/** The task's repeating rules and upcoming sessions; tap a session to edit it. */
@Composable
internal fun SessionsCard(
    schedule: TaskSchedule,
    zone: ZoneId,
    vm: AppViewModel,
    task: Task,
    googleConnected: Boolean,
) {
    val (rules, sessions) = schedule
    Column(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sessions.isEmpty() && rules.isEmpty()) {
            Text(tr("readyWhenYouAre"), style = AppType.label)
            Text(tr("noTimeSet"), style = AppType.small)
            return@Column
        }
        Text(tr("sessions"), style = AppType.label)
        rules.forEach { binding -> RuleRow(binding.rule) { vm.tasks.detachRule(binding.rule.id) } }
        sessions.forEach { session ->
            SessionRow(
                session,
                zone,
                calendarNoteKey(session, googleConnected),
                onEdit = { vm.scheduling = ScheduleTarget(task.id, session) },
                onDelete = { vm.tasks.deleteSession(session) },
            )
        }
    }
}

@Composable
private fun RuleRow(rule: RelativeRule, onRemove: () -> Unit) {
    val relation = tr(relationKey(rule.relation))
    val prayer = prayerName(rule.prayer, LocalLang.current)
    val text =
        "${tr("every")}: $relation $prayer · ${num(rule.offsetMin)}/${num(rule.durationMin)} ${tr("min")}"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = AppType.small, modifier = Modifier.weight(1f))
        AppIconButton(Icons.Outlined.Close, tr("delete"), onRemove, AppColors.secondary)
    }
}

@Composable
private fun SessionRow(
    session: TaskSession,
    zone: ZoneId,
    calendarNote: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val needsReview = session.state == SessionState.NEEDS_REVIEW
    val notes =
        listOfNotNull(
            "↻".takeIf { session.timing is TimingRule.Relative },
            tr("pinned").takeIf { session.pinned },
            tr("needsReview").takeIf { needsReview },
            calendarNote?.let { tr(it) },
        )
    val text =
        (listOf(
                "${shortDate(session.start.atZone(zone).toLocalDate(), LocalLang.current)}, ${num(range(session.start, session.end, zone))}"
            ) + notes)
            .joinToString(" · ")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style =
                AppType.small.copy(
                    color = if (needsReview) AppColors.warningText else AppColors.secondary
                ),
            modifier = Modifier.weight(1f).clickable(onClick = onEdit),
        )
        AppIconButton(Icons.Outlined.Close, tr("delete"), onDelete, AppColors.secondary)
    }
}
