package app.dailylife.android.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dailylife.android.domain.siblingOrder
import app.dailylife.android.navigation.Screen
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.AppIconButton
import app.dailylife.android.ui.components.Field
import app.dailylife.android.ui.components.TaskRowView
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskStatus

/** A task's subtasks in order, with reordering and a field to add one (TSK03). */
@Composable
internal fun Subtasks(vm: AppViewModel, parent: Task, all: List<Task>) {
    val children = all.filter { it.parentId == parent.id }.sortedWith(siblingOrder)
    var title by rememberSaveable(parent.id) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("subtasks"), style = AppType.label)
        children.forEachIndexed { i, child ->
            TaskRowView(
                child.title,
                "",
                child.status == TaskStatus.COMPLETED,
                { vm.tasks.toggleDone(child) },
                { vm.navigator.open(Screen.TaskDetail(child.id)) },
            ) {
                if (i > 0)
                    AppIconButton(
                        Icons.Outlined.KeyboardArrowUp,
                        tr("moveUp"),
                        { vm.tasks.moveSubtask(child, -1) },
                        AppColors.secondary,
                    )
                if (i < children.lastIndex)
                    AppIconButton(
                        Icons.Outlined.KeyboardArrowDown,
                        tr("moveDown"),
                        { vm.tasks.moveSubtask(child, 1) },
                        AppColors.secondary,
                    )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) { Field(title, tr("addSubtask")) { title = it } }
            AppButton(
                "+",
                {
                    vm.tasks.addSubtask(parent, title.trim())
                    title = ""
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.size(52.dp),
            )
        }
    }
}
