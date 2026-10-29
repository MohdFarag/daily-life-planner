package app.dailylife.android.ui.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.dailylife.android.data.Snapshot
import app.dailylife.android.navigation.Screen
import app.dailylife.android.ui.components.Empty
import app.dailylife.android.ui.components.Field
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.TaskRowView
import app.dailylife.android.ui.components.TopBar
import app.dailylife.android.ui.format.listName
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.inbox.InboxCard
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.CaptureState
import app.dailylife.core.model.TaskStatus

@Composable
fun SearchScreen(vm: AppViewModel, s: Snapshot) {
    var q by remember { mutableStateOf("") }
    val query = q.trim().lowercase()
    val tasks =
        if (query.isEmpty()) emptyList()
        else
            s.tasks.filter {
                (it.title + " " + it.notes + " " + it.tags.joinToString(" "))
                    .lowercase()
                    .contains(query)
            }
    val items =
        if (query.isEmpty()) emptyList()
        else
            s.items.filter {
                it.state != CaptureState.TRASHED &&
                    it.state != CaptureState.PROCESSED &&
                    (it.title +
                            " " +
                            it.body +
                            " " +
                            (it.url ?: "") +
                            " " +
                            it.entries.joinToString(" ") { e -> e.text })
                        .lowercase()
                        .contains(query)
            }
    ScreenColumn {
        TopBar(tr("search"), { vm.navigator.back() })
        Field(q, tr("searchHint")) { q = it }
        tasks.forEach { t ->
            TaskRowView(
                t.title,
                listName(s, t.listId).orEmpty(),
                t.status == TaskStatus.COMPLETED,
                { vm.tasks.toggleDone(t) },
                { vm.navigator.open(Screen.TaskDetail(t.id)) },
            )
        }
        items.forEach { i ->
            InboxCard(i, vm.lang) {
                if (i.state == CaptureState.INBOX || i.state == CaptureState.DOING_NOW)
                    vm.navigator.open(Screen.Process(i.id))
                else vm.navigator.open(Screen.Collections)
            }
        }
        if (query.isNotEmpty() && tasks.isEmpty() && items.isEmpty())
            Empty(Icons.Outlined.Search, tr("noResults"))
    }
}
