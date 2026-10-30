package app.dailylife.android.viewmodel

import app.dailylife.android.data.Prefs
import app.dailylife.android.domain.BlockMove
import app.dailylife.android.domain.DataExport
import app.dailylife.android.domain.SessionMove
import app.dailylife.core.model.BlockPolicy
import app.dailylife.core.model.TaskList
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.prayer.PrayerSettings
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.launch

/** Preferences, prayer settings, time blocks, lists and data export. */
class SettingsController(private val ctx: ControllerScope) {

    fun savePrefs(prefs: Prefs) = ctx.scope.launch { ctx.repository.savePrefs(prefs) }

    /** New prayer settings move dependent future sessions (AC10). */
    fun savePrayerSettings(settings: PrayerSettings) =
        ctx.scope.launch {
            ctx.repository.savePrayerSettings(settings)
            ctx.repository.savePrefs(ctx.snapshot.prefs.copy(prayerConfirmed = true))
            ctx.container.ruleScheduler.refreshAll(ctx.today())
        }

    /** Adds an every-day guiding block, 14:00–16:00, for the user to adjust. */
    fun addBlock(name: String) =
        ctx.scope.launch {
            val days = DayOfWeek.entries.toSet()
            ctx.repository.saveBlock(
                TimeBlock(
                    "block-${ctx.newId()}",
                    name,
                    LocalTime.of(14, 0),
                    LocalTime.of(16, 0),
                    days,
                    BlockPolicy.GUIDING,
                )
            )
        }

    /** What retiming [block] would do to its sessions; empty when nothing moves (BLK04). */
    fun previewBlockMove(block: TimeBlock): List<SessionMove> {
        val old = ctx.snapshot.blocks.firstOrNull { it.id == block.id } ?: return emptyList()
        return BlockMove.preview(
            old,
            block,
            ctx.snapshot.sessions,
            ctx.snapshot.prayer.zone,
            ctx.now(),
        )
    }

    /** Applies a previewed block move: the block and its sessions together. */
    fun moveBlock(block: TimeBlock, moves: List<SessionMove>) =
        ctx.scope.launch {
            ctx.repository.saveBlock(block)
            if (moves.isNotEmpty()) ctx.repository.saveSessions(moves.map { it.after })
        }

    fun saveBlock(block: TimeBlock) = ctx.scope.launch { ctx.repository.saveBlock(block) }

    fun deleteBlock(block: TimeBlock) = ctx.scope.launch { ctx.repository.deleteBlock(block.id) }

    fun addList(name: String) =
        ctx.scope.launch { ctx.repository.saveList(TaskList(ctx.newId(), name)) }

    fun exportJson(): String = DataExport.toJson(ctx.snapshot, ctx.now())
}
