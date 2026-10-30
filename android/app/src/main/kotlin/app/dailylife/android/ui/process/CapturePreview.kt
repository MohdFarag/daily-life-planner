package app.dailylife.android.ui.process

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.dailylife.android.ui.components.Badge
import app.dailylife.android.ui.components.Corner24
import app.dailylife.android.ui.components.ImagePreview
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.components.kindIcon
import app.dailylife.android.ui.format.attachmentLabel
import app.dailylife.android.ui.format.itemTitle
import app.dailylife.android.ui.format.kindKey
import app.dailylife.android.ui.format.relative
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.core.model.CaptureItem
import java.io.File

@Composable
internal fun CapturePreview(item: CaptureItem, lang: String) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().card(Corner24).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Badge(tr(kindKey(item.kind)), AppColors.warning, AppColors.warningText)
        Text(itemTitle(item, lang), style = AppType.h2)
        if (item.body.isNotBlank())
            Text(item.body, style = AppType.body.copy(color = AppColors.secondary))
        item.url?.let { url ->
            Row(
                Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.OpenInNew,
                    null,
                    tint = AppColors.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(url, style = AppType.label, color = AppColors.primary)
            }
        }
        item.attachments.forEach { a ->
            val open = Modifier.clickable {
                runCatching {
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.files",
                            File(a.localPath),
                        )
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, a.mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
            }
            if (a.mimeType.startsWith("image"))
                ImagePreview(a.localPath, attachmentLabel(a, lang), open)
            else
                Text(
                    "${attachmentLabel(a, lang)} · ${num(a.sizeBytes / 1024)} KB",
                    style = AppType.label,
                    color = AppColors.primary,
                    modifier = open,
                )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                kindIcon(item.kind),
                null,
                tint = AppColors.secondary,
                modifier = Modifier.size(18.dp),
            )
            Text(tr("capturedAgo", relative(item.capturedAt, lang)), style = AppType.micro)
        }
    }
}
