package app.dailylife.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.Snapshot
import app.dailylife.android.navigation.Screen
import app.dailylife.android.navigation.isTabRoot
import app.dailylife.android.navigation.showsBottomBar
import app.dailylife.android.ui.capture.CaptureSheet
import app.dailylife.android.ui.collections.CollectionsScreen
import app.dailylife.android.ui.collections.TasksScreen
import app.dailylife.android.ui.collections.TrashScreen
import app.dailylife.android.ui.components.BottomNav
import app.dailylife.android.ui.focus.FocusScreen
import app.dailylife.android.ui.i18n.LocalLang
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.inbox.InboxScreen
import app.dailylife.android.ui.more.MoreScreen
import app.dailylife.android.ui.plan.PlanScreen
import app.dailylife.android.ui.plan.ScheduleSheet
import app.dailylife.android.ui.process.ProcessScreen
import app.dailylife.android.ui.search.SearchScreen
import app.dailylife.android.ui.settings.BlocksScreen
import app.dailylife.android.ui.settings.ListsScreen
import app.dailylife.android.ui.settings.PrayerSettingsScreen
import app.dailylife.android.ui.settings.PreferencesScreen
import app.dailylife.android.ui.task.TaskDetailScreen
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.ui.today.TodayScreen
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.android.viewmodel.DuplicateShare
import app.dailylife.android.viewmodel.UiMessage
import kotlinx.coroutines.delay

private const val MESSAGE_MILLIS = 4_000L

/**
 * The app shell: current screen, bottom navigation, capture button, sheets, messages and dialogs.
 */
@Composable
fun AppRoot(vm: AppViewModel, onExit: () -> Unit) {
    val s by vm.state.collectAsState()
    val screen = vm.navigator.current
    BackHandler { if (!vm.navigator.back()) onExit() }

    CompositionLocalProvider(
        LocalLang provides s.lang,
        LocalLayoutDirection provides
            if (s.lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        Box(Modifier.fillMaxSize().background(AppColors.canvas)) {
            if (!s.loaded) return@Box
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f)) {
                    ScreenHost(screen, vm, s)
                    if (screen.isTabRoot) CaptureButton { vm.captureOpen = true }
                    vm.messages.current?.let {
                        MessageBar(
                            it,
                            aboveBottomBar = screen.isTabRoot,
                            onDismiss = vm.messages::dismiss,
                        )
                    }
                }
                if (screen.showsBottomBar)
                    BottomNav(vm.navigator.currentTab, vm.navigator::switchTo)
            }
            if (vm.captureOpen) CaptureSheet(vm)
            vm.scheduling?.let { ScheduleSheet(vm, s, it) }
            vm.capture.duplicate?.let { DuplicateShareDialog(it, vm) }
        }
    }
}

@Composable
private fun ScreenHost(screen: Screen, vm: AppViewModel, s: Snapshot) =
    when (screen) {
        Screen.Today -> TodayScreen(vm, s)
        Screen.Inbox -> InboxScreen(vm, s)
        Screen.Plan -> PlanScreen(vm, s)
        Screen.More -> MoreScreen(vm, s)
        is Screen.Process -> ProcessScreen(vm, s, screen.itemId)
        is Screen.TaskDetail -> TaskDetailScreen(vm, s, screen.id, screen.fresh)
        Screen.Focus -> FocusScreen(vm, s)
        Screen.Search -> SearchScreen(vm, s)
        Screen.Tasks -> TasksScreen(vm, s)
        Screen.Collections -> CollectionsScreen(vm, s)
        Screen.Trash -> TrashScreen(vm, s)
        Screen.PrayerSettings -> PrayerSettingsScreen(vm, s)
        Screen.Blocks -> BlocksScreen(vm, s)
        Screen.Lists -> ListsScreen(vm, s)
        Screen.Preferences -> PreferencesScreen(vm, s)
    }

@Composable
private fun BoxScope.CaptureButton(onClick: () -> Unit) {
    val label = tr("captureSomething")
    Box(
        Modifier.align(Alignment.BottomEnd)
            .padding(24.dp)
            .size(56.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.primary)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, null, tint = AppColors.onPrimary)
    }
}

@Composable
private fun BoxScope.MessageBar(
    message: UiMessage,
    aboveBottomBar: Boolean,
    onDismiss: (UiMessage) -> Unit,
) {
    LaunchedEffect(message.id) {
        delay(MESSAGE_MILLIS)
        onDismiss(message)
    }
    val text = if (message.argKey != null) tr(message.key, tr(message.argKey)) else tr(message.key)
    Row(
        Modifier.align(Alignment.BottomCenter)
            .padding(start = 24.dp, end = 24.dp, bottom = if (aboveBottomBar) 96.dp else 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.dark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = AppType.label,
            color = AppColors.onPrimary,
            modifier = Modifier.weight(1f),
        )
        message.undo?.let { undo ->
            Text(
                tr("undo"),
                style = AppType.label,
                color = AppColors.highlight,
                modifier = Modifier.clickable { undo() }.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun DuplicateShareDialog(duplicate: DuplicateShare, vm: AppViewModel) {
    AlertDialog(
        onDismissRequest = vm.capture::keepDuplicate,
        title = { Text(tr("duplicateShare")) },
        text = { Text(duplicate.existing.title) },
        confirmButton = {
            TextButton(vm.capture::openExistingInsteadOfDuplicate) { Text(tr("openExisting")) }
        },
        dismissButton = { TextButton(vm.capture::keepDuplicate) { Text(tr("keepNew")) } },
    )
}
