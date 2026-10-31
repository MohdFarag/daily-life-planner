package app.dailylife.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.Snapshot
import app.dailylife.android.domain.SessionMove
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Chip
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.Segmented
import app.dailylife.android.ui.components.TopBar
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.components.pickTime
import app.dailylife.android.ui.components.rememberDraft
import app.dailylife.android.ui.format.blockLabel
import app.dailylife.android.ui.format.listLabel
import app.dailylife.android.ui.format.locale
import app.dailylife.android.ui.format.range
import app.dailylife.android.ui.i18n.LocalLang
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.BlockPolicy
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskList
import app.dailylife.core.model.TimeBlock
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
fun BlocksScreen(vm: AppViewModel, s: Snapshot) {
    // A new time for a block, waiting for the user to confirm how its sessions move (BLK04).
    var pending by remember { mutableStateOf<Pair<TimeBlock, List<SessionMove>>?>(null) }
    ScreenColumn {
        TopBar(tr("timeBlocks"), { vm.navigator.back() })
        pending?.let { (block, moves) ->
            MovePreview(
                s,
                moves,
                onApply = {
                    vm.settings.moveBlock(block, moves)
                    pending = null
                },
                onCancel = { pending = null },
            )
        }
        s.blocks.forEach { block ->
            BlockCard(
                block,
                s.lists,
                onChange = vm.settings::saveBlock,
                onRetime = { retimed ->
                    val moves = vm.settings.previewBlockMove(retimed)
                    if (moves.isEmpty()) vm.settings.saveBlock(retimed)
                    else pending = retimed to moves
                },
                onDelete = { vm.settings.deleteBlock(block) },
            )
        }
        val newName = tr("timeBlocks")
        AppButton(tr("addBlock"), { vm.settings.addBlock(newName) }, style = ButtonStyle.SECONDARY)
    }
}

/** Name, hours, guiding or protected, days and allowed lists of one block (BLK03). */
@Composable
private fun BlockCard(
    block: TimeBlock,
    lists: List<TaskList>,
    onChange: (TimeBlock) -> Unit,
    onRetime: (TimeBlock) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val lang = LocalLang.current
    Column(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val latest by rememberUpdatedState(block)
        var name by
            rememberDraft(block.id, blockLabel(block, lang)) { onChange(latest.copy(name = it)) }
        BasicTextField(
            name,
            { name = it },
            textStyle = AppType.body,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip(
                "${tr("from")} ${num(block.start.toString())}",
                false,
                {
                    pickTime(context, block.start) {
                        if (it.isBefore(block.end)) onRetime(block.copy(start = it))
                    }
                },
            )
            Chip(
                "${tr("to")} ${num(block.end.toString())}",
                false,
                {
                    pickTime(context, block.end) {
                        if (it.isAfter(block.start)) onRetime(block.copy(end = it))
                    }
                },
            )
        }
        Segmented(
            listOf(
                BlockPolicy.GUIDING.name to tr("guiding"),
                BlockPolicy.PROTECTED.name to tr("protected"),
            ),
            block.policy.name,
            { onChange(block.copy(policy = BlockPolicy.valueOf(it))) },
            Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                Chip(
                    day.getDisplayName(TextStyle.SHORT, locale(lang)),
                    day in block.days,
                    { onChange(block.copy(days = block.days.toggle(day))) },
                )
            }
        }
        Text(tr("allowedLists"), style = AppType.micro)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lists.forEach { list ->
                Chip(
                    listLabel(list, lang),
                    list.id in block.allowedListIds,
                    { onChange(block.copy(allowedListIds = block.allowedListIds.toggle(list.id))) },
                )
            }
        }
        Text(
            tr("delete"),
            style = AppType.label,
            color = AppColors.danger,
            modifier = Modifier.clickable(onClick = onDelete).padding(vertical = 8.dp),
        )
    }
}

/** Lists how each linked session would move before the block's new time is applied. */
@Composable
private fun MovePreview(
    s: Snapshot,
    moves: List<SessionMove>,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    val zone = s.prayer.zone
    val titles = s.tasks.associate { it.id to it.title }
    Column(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("blockMoveTitle", moves.size), style = AppType.label)
        moves.forEach { (before, after) ->
            val review =
                if (after.state == SessionState.NEEDS_REVIEW) " · ${tr("needsReview")}" else ""
            Text(
                "${titles[before.taskId].orEmpty()}: ${num(range(before.start, before.end, zone))} → ${num(range(after.start, after.end, zone))}$review",
                style = AppType.small,
            )
        }
        AppButton(tr("apply"), onApply)
        AppButton(tr("cancel"), onCancel, style = ButtonStyle.QUIET)
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
