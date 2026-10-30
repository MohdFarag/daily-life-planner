package app.dailylife.android.navigation

/** Every destination in the app. Tab roots first, then screens opened on top of them. */
sealed interface Screen {
    data object Today : Screen

    data object Inbox : Screen

    data object Plan : Screen

    data object More : Screen

    data class Process(val itemId: String? = null) : Screen

    data class TaskDetail(val id: String, val fresh: Boolean = false) : Screen

    data object Focus : Screen

    data object Search : Screen

    data object Tasks : Screen

    data object Collections : Screen

    data object Trash : Screen

    data object PrayerSettings : Screen

    data object Blocks : Screen

    data object Lists : Screen

    data object Preferences : Screen
}

enum class Tab(val root: Screen) {
    TODAY(Screen.Today),
    INBOX(Screen.Inbox),
    PLAN(Screen.Plan),
    MORE(Screen.More),
}

/** Tab roots and the secondary screens that keep the bottom navigation visible. */
val Screen.isTabRoot: Boolean
    get() = Tab.entries.any { it.root == this }
val Screen.showsBottomBar: Boolean
    get() =
        isTabRoot || this in setOf(Screen.Search, Screen.Tasks, Screen.Collections, Screen.Trash)
