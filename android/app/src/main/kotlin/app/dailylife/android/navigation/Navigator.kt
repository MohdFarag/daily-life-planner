package app.dailylife.android.navigation

import androidx.compose.runtime.mutableStateListOf

/** A back stack per tab: switching tabs starts a fresh stack; Back pops within it. */
class Navigator {
    private val stack = mutableStateListOf<Screen>(Screen.Today)

    val current: Screen
        get() = stack.last()

    val currentTab: Tab
        get() = Tab.entries.firstOrNull { it.root == stack.first() } ?: Tab.TODAY

    fun open(screen: Screen) {
        stack += screen
    }

    fun replace(screen: Screen) {
        stack[stack.lastIndex] = screen
    }

    /** Returns false when already at a tab root, so the caller can leave the app. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun switchTo(tab: Tab) {
        stack.clear()
        stack += tab.root
    }
}
