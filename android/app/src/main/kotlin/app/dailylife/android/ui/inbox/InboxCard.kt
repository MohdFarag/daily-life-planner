package app.dailylife.android.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dailylife.android.ui.components.Corner12
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.components.kindIcon
import app.dailylife.android.ui.format.itemTitle
import app.dailylife.android.ui.format.kindKey
import app.dailylife.android.ui.format.relative
import app.dailylife.android.ui.format.sourceLabel
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind

@Composable
fun InboxCard(item: CaptureItem, lang: String, onClick: () -> Unit) {
    val origin = sourceLabel(item.source.service)
    val subtitle =
        (if (origin != null) tr("sharedFrom", origin) else tr(kindKey(item.kind))) +
            " · " +
            relative(item.capturedAt, lang)
    val detail =
        when {
            item.kind == CaptureKind.LIST -> item.entries.joinToString(" · ") { it.text }
            item.kind == CaptureKind.LINK -> item.url.orEmpty()
            item.attachments.isNotEmpty() && item.body.isBlank() ->
                tr("attachments", item.attachments.size)
            else -> item.body.lineSequence().firstOrNull().orEmpty()
        }
    Row(
        Modifier.fillMaxWidth().card().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(Corner12).background(AppColors.tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(kindIcon(item.kind), null, tint = AppColors.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                itemTitle(item, lang),
                style = AppType.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = AppType.micro)
            if (detail.isNotBlank())
                Text(detail, style = AppType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
