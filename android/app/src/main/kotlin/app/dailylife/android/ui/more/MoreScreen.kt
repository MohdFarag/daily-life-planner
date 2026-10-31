package app.dailylife.android.ui.more

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dailylife.android.data.Snapshot
import app.dailylife.android.navigation.Screen
import app.dailylife.android.ui.components.Corner12
import app.dailylife.android.ui.components.PageHeading
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.SectionLabel
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.format.cityLabel
import app.dailylife.android.ui.format.listLabel
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.TaskStatus
import java.io.File

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 66.dp)
            .card()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(Corner12).background(AppColors.tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = AppColors.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.body)
            Text(subtitle, style = AppType.micro)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AppColors.secondary)
    }
}

@Composable
fun MoreScreen(vm: AppViewModel, s: Snapshot) {
    val context = LocalContext.current
    ScreenColumn {
        PageHeading(tr("more"), tr("moreSubtitle"), null)
        SectionLabel(tr("yourSpace"))
        NavRow(
            Icons.Outlined.Checklist,
            tr("allTasks"),
            tr("openCount", s.tasks.count { it.status != TaskStatus.COMPLETED }),
        ) {
            vm.navigator.open(Screen.Tasks)
        }
        NavRow(Icons.Outlined.FolderOpen, tr("collections"), tr("collectionsSub")) {
            vm.navigator.open(Screen.Collections)
        }
        SectionLabel(tr("settings"))
        val account = vm.account.account.collectAsStateWithLifecycle().value
        NavRow(Icons.Outlined.Sync, tr("accountSync"), account?.email ?: tr("localOnly")) {
            vm.navigator.open(Screen.Account)
        }
        NavRow(Icons.Outlined.NightsStay, tr("prayerTimes"), cityLabel(s.prayer, s.lang)) {
            vm.navigator.open(Screen.PrayerSettings)
        }
        NavRow(Icons.Outlined.ViewTimeline, tr("timeBlocks"), tr("timeBlocksSub")) {
            vm.navigator.open(Screen.Blocks)
        }
        NavRow(
            Icons.AutoMirrored.Outlined.List,
            tr("lists"),
            s.lists.joinToString { listLabel(it, s.lang) },
        ) {
            vm.navigator.open(Screen.Lists)
        }
        NavRow(Icons.Outlined.Tune, tr("preferences"), tr("preferencesSub")) {
            vm.navigator.open(Screen.Preferences)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                tr("exportData"),
                style = AppType.label,
                color = AppColors.primary,
                modifier =
                    Modifier.clickable {
                            // Documented, restorable JSON export shared through the system sheet
                            // (SEC02).
                            val f =
                                File(vm.mediaDir, "daily-life-planner-export.json").apply {
                                    writeText(vm.settings.exportJson())
                                }
                            val uri =
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.files",
                                    f,
                                )
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .setType("application/json")
                                        .putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    null,
                                )
                            )
                        }
                        .padding(vertical = 12.dp),
            )
            Text(
                tr("trash"),
                style = AppType.label,
                color = AppColors.primary,
                modifier =
                    Modifier.clickable { vm.navigator.open(Screen.Trash) }
                        .padding(vertical = 12.dp),
            )
        }
    }
}
