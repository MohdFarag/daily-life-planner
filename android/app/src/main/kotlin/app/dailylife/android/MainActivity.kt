package app.dailylife.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import app.dailylife.android.ui.BottomNav
import app.dailylife.android.ui.C
import app.dailylife.android.ui.LocalLang
import app.dailylife.android.ui.PlannerTheme
import app.dailylife.android.ui.T
import app.dailylife.android.ui.screens.BlocksScreen
import app.dailylife.android.ui.screens.CaptureSheet
import app.dailylife.android.ui.screens.CollectionsScreen
import app.dailylife.android.ui.screens.FocusScreen
import app.dailylife.android.ui.screens.InboxScreen
import app.dailylife.android.ui.screens.ListsScreen
import app.dailylife.android.ui.screens.MoreScreen
import app.dailylife.android.ui.screens.PlanScreen
import app.dailylife.android.ui.screens.PrayerSettingsScreen
import app.dailylife.android.ui.screens.PreferencesScreen
import app.dailylife.android.ui.screens.ProcessScreen
import app.dailylife.android.ui.screens.ScheduleSheet
import app.dailylife.android.ui.screens.SearchScreen
import app.dailylife.android.ui.screens.TaskDetailScreen
import app.dailylife.android.ui.screens.TasksScreen
import app.dailylife.android.ui.screens.TodayScreen
import app.dailylife.android.ui.screens.TrashScreen
import app.dailylife.android.ui.tr
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { PlannerTheme { Root(vm) { finish() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("openTask")?.let { vm.go(Screen.TaskDetail(it)) }
        if (intent.hasExtra("openWaiting")) vm.go(Screen.Collections)
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (text.isNullOrBlank() && uris.isEmpty()) return
        // Shared items enter the inbox unprocessed; sharing alone never marks anything done (CAP05).
        vm.receiveShare(SharePayload(text, intent.getStringExtra(Intent.EXTRA_SUBJECT), uris, intent.type, referrer?.host))
        vm.tab(Tab.INBOX)
    }
}

private val TAB_SCREENS = setOf(Screen.Today, Screen.Inbox, Screen.Plan, Screen.More)

@Composable
private fun Root(vm: AppViewModel, exit: () -> Unit) {
    val s by vm.state.collectAsState()
    val screen = vm.screen
    BackHandler { if (!vm.back()) exit() }

    CompositionLocalProvider(
        LocalLang provides s.prefs.lang,
        LocalLayoutDirection provides if (s.prefs.lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        Box(Modifier.fillMaxSize().background(C.canvas)) {
            if (!s.loaded) return@Box
            val isTab = screen in TAB_SCREENS
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f)) {
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
                    if (isTab) {
                        val label = tr("captureSomething")
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(24.dp).size(56.dp)
                                .shadow(6.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(C.primary)
                                .clickable { vm.captureOpen = true }.semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Add, null, tint = C.onPrimary) }
                    }
                    vm.toast?.let { t ->
                        LaunchedEffect(t.id) { delay(4000); if (vm.toast?.id == t.id) vm.toast = null }
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = if (isTab) 96.dp else 24.dp)
                                .fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.dark).padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t.message, style = T.label, color = C.onPrimary, modifier = Modifier.weight(1f))
                            t.undo?.let { u -> Text(tr("undo"), style = T.label, color = C.highlight, modifier = Modifier.clickable { u() }.padding(start = 12.dp)) }
                        }
                    }
                }
                if (isTab || screen in setOf(Screen.Search, Screen.Tasks, Screen.Collections, Screen.Trash)) BottomNav(vm.currentTab()) { vm.tab(it) }
            }
            if (vm.captureOpen) CaptureSheet(vm)
            vm.scheduleTaskId?.let { ScheduleSheet(vm, s, it) }
            vm.duplicateOf?.let { (existing, newId) ->
                AlertDialog(
                    onDismissRequest = { vm.duplicateOf = null },
                    title = { Text(tr("duplicateShare")) },
                    text = { Text(existing.title) },
                    confirmButton = { TextButton({ vm.duplicateOf = null; vm.deleteForever(listOf(newId)); vm.go(Screen.Process(existing.id)) }) { Text(tr("openExisting")) } },
                    dismissButton = { TextButton({ vm.duplicateOf = null }) { Text(tr("keepNew")) } },
                )
            }
        }
    }
}
