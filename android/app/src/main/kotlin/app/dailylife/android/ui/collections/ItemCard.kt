package app.dailylife.android.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.components.kindIcon
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.core.model.CaptureItem

@Composable
internal fun ItemCard(
    item: CaptureItem,
    actions: List<Pair<String, () -> Unit>>,
    extra: String? = null,
) {
    Column(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(kindIcon(item.kind), null, tint = AppColors.primary)
            Column(Modifier.weight(1f)) {
                Text(item.title, style = AppType.body)
                if (item.body.isNotBlank())
                    Text(item.body.lineSequence().first(), style = AppType.small)
                if (extra != null) Text(extra, style = AppType.micro)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
        ) {
            actions.forEach { (label, action) ->
                Text(
                    label,
                    style = AppType.label,
                    color = AppColors.primary,
                    modifier = Modifier.clickable(onClick = action).padding(8.dp),
                )
            }
        }
    }
}
