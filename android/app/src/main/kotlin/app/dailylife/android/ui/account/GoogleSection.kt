package app.dailylife.android.ui.account

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.dailylife.android.navigation.Screen
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.DetailRow
import app.dailylife.android.ui.components.SectionLabel
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel

/**
 * Google Tasks and Calendar (§6). Connecting opens Google's consent page in the browser; the server
 * keeps the tokens, so they never reach this device (SEC01).
 */
@Composable
fun GoogleSection(vm: AppViewModel) {
    val google = vm.google
    val context = LocalContext.current
    LaunchedEffect(Unit) { google.refresh() }
    val status = google.status ?: return

    SectionLabel(tr("googleSection"))
    when {
        !status.enabled -> Text(tr("googleNotConfigured"), style = AppType.small)
        !status.connected -> {
            Text(tr("googleExplain"), style = AppType.small)
            AppButton(
                tr("connectGoogle"),
                {
                    google.connect { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                enabled = !google.busy,
            )
        }
        else -> {
            DetailRow(tr("googleAccount"), status.email.orEmpty())
            status.lastError?.let {
                Text(
                    if (it == "reconnect_required") tr("googleReconnect")
                    else tr("googleSyncProblem", it),
                    style = AppType.small.copy(color = AppColors.warningText),
                )
            }
            AppButton(
                tr("syncNow"),
                { google.syncNow() },
                enabled = !google.busy,
                style = ButtonStyle.SECONDARY,
            )
            DetailRow(tr("calendars"), tr("calendarsSub")) { vm.navigator.open(Screen.Calendars) }
            AppButton(
                tr("disconnectGoogle"),
                { google.disconnect() },
                enabled = !google.busy,
                style = ButtonStyle.QUIET,
            )
        }
    }
    google.error?.let {
        Text(
            it.detail?.let { d -> tr(it.key) + ": " + d } ?: tr(it.key),
            style = AppType.small.copy(color = AppColors.warningText),
        )
    }
}
