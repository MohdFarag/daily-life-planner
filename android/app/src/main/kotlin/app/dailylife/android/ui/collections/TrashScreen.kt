package app.dailylife.android.ui.collections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Empty
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.TopBar
import app.dailylife.android.ui.format.shortDate
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.CaptureState

@Composable
fun TrashScreen(vm: AppViewModel, s: Snapshot) {
    val items = s.items.filter { it.state == CaptureState.TRASHED }
    ScreenColumn {
        TopBar(tr("trash"), { vm.navigator.back() })
        if (items.isEmpty()) Empty(Icons.Outlined.DeleteOutline, tr("trashEmpty"))
        items.forEach { i ->
            ItemCard(
                i,
                listOf(
                    tr("restore") to
                        {
                            vm.inbox.restore(i)
                            Unit
                        },
                    tr("deleteForever") to
                        {
                            vm.inbox.deleteForever(listOf(i.id))
                            Unit
                        },
                ),
                i.purgeAt?.let {
                    tr("purgeOn", shortDate(it.atZone(s.prayer.zone).toLocalDate(), vm.lang))
                },
            )
        }
        if (items.isNotEmpty())
            AppButton(
                tr("emptyTrash"),
                { vm.inbox.deleteForever(items.map { it.id }) },
                style = ButtonStyle.SECONDARY,
            )
    }
}
