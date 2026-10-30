package app.dailylife.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType

@Composable
fun Checkbox(checked: Boolean) {
    Box(
        Modifier.size(24.dp)
            .clip(Corner8)
            .background(if (checked) AppColors.primary else AppColors.surface)
            .border(1.5.dp, if (checked) AppColors.primary else AppColors.border, Corner8),
        contentAlignment = Alignment.Center,
    ) {
        if (checked)
            Icon(
                Icons.Outlined.Check,
                null,
                tint = AppColors.onPrimary,
                modifier = Modifier.size(16.dp),
            )
    }
}

@Composable
fun TaskRowView(
    title: String,
    meta: String,
    done: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val doneLabel = tr("complete")
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp).card().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Completion only follows an explicit tap on the checkbox.
        Box(
            Modifier.size(width = 36.dp, height = 48.dp)
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .semantics {
                    contentDescription = doneLabel
                    stateDescription = if (done) "done" else "open"
                },
            contentAlignment = Alignment.Center,
        ) {
            Checkbox(done)
        }
        Column(
            Modifier.weight(1f).clickable(onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style =
                    AppType.body.copy(
                        color = if (done) AppColors.secondary else AppColors.text,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(meta, style = AppType.micro)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun PrayerRow(name: String, time: String) {
    Row(
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clip(Corner16)
            .background(AppColors.tint)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.NightsStay, null, tint = AppColors.primary)
        Column {
            Text(name, style = AppType.label, color = AppColors.primary)
            Text("$time · ${tr("protectedTime")}", style = AppType.micro)
        }
    }
}
