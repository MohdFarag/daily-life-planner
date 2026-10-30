package app.dailylife.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.DetailRow
import app.dailylife.android.ui.components.Field
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.TopBar
import app.dailylife.android.ui.format.listLabel
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.TaskStatus

@Composable
fun ListsScreen(vm: AppViewModel, s: Snapshot) {
    var name by remember { mutableStateOf("") }
    ScreenColumn {
        TopBar(tr("lists"), { vm.navigator.back() })
        s.lists.forEach { l ->
            DetailRow(
                listLabel(l, s.lang),
                tr(
                    "openCount",
                    s.tasks.count { it.listId == l.id && it.status != TaskStatus.COMPLETED },
                ),
            )
        }
        Field(name, tr("newList")) { name = it }
        AppButton(
            tr("addList"),
            {
                vm.settings.addList(name.trim())
                name = ""
            },
            enabled = name.isNotBlank(),
        )
    }
}
