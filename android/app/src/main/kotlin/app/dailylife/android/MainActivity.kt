package app.dailylife.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import app.dailylife.android.navigation.Screen
import app.dailylife.android.navigation.Tab
import app.dailylife.android.ui.AppRoot
import app.dailylife.android.ui.theme.PlannerTheme
import app.dailylife.android.viewmodel.AppViewModel

/** Hosts the Compose UI and routes incoming intents (shares, notification taps). */
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        requestNotificationPermission()
        setContent { PlannerTheme { AppRoot(vm, onExit = ::finish) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(LaunchExtras.OPEN_TASK)?.let {
            vm.navigator.open(Screen.TaskDetail(it))
        }
        if (intent.hasExtra(LaunchExtras.OPEN_WAITING)) vm.navigator.open(Screen.Collections)
        // Shared items enter the inbox unprocessed; sharing alone never marks anything done
        // (CAP05).
        intent.toSharePayload(referrer?.host)?.let { payload ->
            vm.capture.receiveShare(payload)
            vm.navigator.switchTo(Tab.INBOX)
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
