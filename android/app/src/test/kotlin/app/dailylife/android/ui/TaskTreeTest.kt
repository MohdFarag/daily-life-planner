package app.dailylife.android.ui

import app.dailylife.android.ui.collections.moveAmongSiblings
import app.dailylife.android.ui.collections.taskTree
import app.dailylife.android.ui.task.TaskEdits
import app.dailylife.core.model.Task
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskTreeTest {
    private fun task(
        id: String,
        parent: String? = null,
        position: String? = null,
        minute: Long = 0,
    ) =
        Task(
            id,
            id,
            listId = "work",
            parentId = parent,
            position = position,
            createdAt = Instant.EPOCH.plusSeconds(minute * 60),
        )

    @Test
    fun `subtasks follow their parent in position order, orphans show at the top level`() {
        val tasks =
            listOf(
                task("b"),
                task("a"),
                task("a2", parent = "a", position = "2"),
                task("a1", parent = "a", position = "1"),
                task("a1x", parent = "a1"),
                task("orphan", parent = "gone"),
            )
        val tree = taskTree(tasks, compareBy { it.id })
        assertEquals(listOf("a", "a1", "a1x", "a2", "b", "orphan"), tree.map { it.task.id })
        assertEquals(listOf(0, 1, 2, 1, 0, 0), tree.map { it.depth })
    }

    @Test
    fun `moving a subtask renumbers its siblings and returns only changed ones`() {
        val siblings =
            listOf(
                task("x", "p", "00000000000000000000"),
                task("y", "p", "00000000000000000001"),
                task("z", "p", "00000000000000000002"),
            )
        val moved = moveAmongSiblings(siblings, "z", -1)
        assertEquals(
            listOf("z" to "00000000000000000001", "y" to "00000000000000000002"),
            moved.map { it.id to it.position },
        )
        assertEquals(emptyList(), moveAmongSiblings(siblings, "x", -1))
    }

    @Test
    fun `context cycles through the offered places and back to anywhere`() {
        var t = task("t")
        val seen =
            (1..TaskEdits.CONTEXTS.size).map {
                t = TaskEdits.nextContext(t)
                t.context
            }
        assertEquals(TaskEdits.CONTEXTS.drop(1) + listOf(null), seen)
    }
}
