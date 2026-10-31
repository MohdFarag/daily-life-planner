package app.dailylife.android.ui.collections

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.Snapshot
import app.dailylife.android.domain.taskTree
import app.dailylife.android.navigation.Screen
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.Chip
import app.dailylife.android.ui.components.Field
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.TaskRowView
import app.dailylife.android.ui.components.TopBar
import app.dailylife.android.ui.format.listLabel
import app.dailylife.android.ui.format.listName
import app.dailylife.android.ui.format.shortDate
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.task.TaskEdits
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.TaskStatus

@Composable
fun TasksScreen(vm: AppViewModel, s: Snapshot) {
    var filter by remember { mutableStateOf("all") }
    var context by remember { mutableStateOf<String?>(null) }
    var newTitle by remember { mutableStateOf("") }
    val shown =
        s.tasks.filter {
            (filter == "all" || it.listId == filter) && (context == null || it.context == context)
        }
    val tasks =
        taskTree(
            shown,
            compareBy(
                { it.status == TaskStatus.COMPLETED },
                { !it.important },
                { it.deadline ?: java.time.LocalDate.MAX },
            ),
        )
    ScreenColumn {
        TopBar(tr("allTasks"), { vm.navigator.back() })
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(tr("all"), filter == "all", { filter = "all" })
            s.lists.forEach { l -> Chip(listLabel(l, s.lang), filter == l.id, { filter = l.id }) }
        }
        // Where the task can be done (ORG01); tap again to clear.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskEdits.CONTEXTS.filterNotNull().forEach { c ->
                Chip(tr("context_$c"), context == c, { context = if (context == c) null else c })
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) { Field(newTitle, tr("newTask")) { newTitle = it } }
            AppButton(
                "+",
                {
                    vm.tasks.add(
                        newTitle.trim(),
                        if (filter == "all") s.lists.first().id else filter,
                    )
                    newTitle = ""
                },
                enabled = newTitle.isNotBlank(),
                modifier = Modifier.size(52.dp),
            )
        }
        tasks.forEach { (t, depth) ->
            val list = listName(s, t.listId).orEmpty()
            val meta =
                listOfNotNull(
                        list,
                        t.deadline?.let { "${tr("deadline")} ${shortDate(it, vm.lang)}" },
                        if (t.important) "★" else null,
                        t.tags.joinToString(" ") { "#$it" }.ifEmpty { null },
                    )
                    .joinToString(" · ")
            Box(Modifier.padding(start = (depth * 24).dp)) {
                TaskRowView(
                    t.title,
                    meta,
                    t.status == TaskStatus.COMPLETED,
                    { vm.tasks.toggleDone(t) },
                    { vm.navigator.open(Screen.TaskDetail(t.id)) },
                )
            }
        }
    }
}
