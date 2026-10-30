package app.dailylife.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType

val Corner8 = RoundedCornerShape(8.dp)

val Corner12 = RoundedCornerShape(12.dp)

val Corner16 = RoundedCornerShape(16.dp)

val Corner24 = RoundedCornerShape(24.dp)

fun Modifier.card(shape: RoundedCornerShape = Corner16, bg: Color = AppColors.surface) =
    this.clip(shape).background(bg).border(1.dp, AppColors.border, shape)

@Composable
fun Badge(
    text: String,
    bg: Color = AppColors.highlight,
    fg: Color = AppColors.dark,
    onClick: (() -> Unit)? = null,
) {
    Box(
        Modifier.height(28.dp)
            .clip(Corner8)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.micro, color = fg)
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .height(44.dp)
            .clip(Corner12)
            .background(if (selected) AppColors.primary else AppColors.surface)
            .border(1.dp, if (selected) AppColors.primary else AppColors.border, Corner12)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null)
            Icon(
                icon,
                null,
                tint = if (selected) AppColors.onPrimary else AppColors.secondary,
                modifier = Modifier.size(18.dp),
            )
        Text(
            text,
            style = AppType.label,
            color = if (selected) AppColors.onPrimary else AppColors.secondary,
        )
    }
}

@Composable
fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(Corner12).background(AppColors.subtle).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.weight(1f)
                    .height(36.dp)
                    .clip(Corner8)
                    .background(if (on) AppColors.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = AppType.label,
                    color = if (on) AppColors.primary else AppColors.secondary,
                )
            }
        }
    }
}

/** A labelled row inside a details card. */
@Composable
fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AppType.small)
        Text(value, style = AppType.label)
    }
}

@Composable
fun SectionLabel(text: String) =
    Text(text, style = AppType.micro, modifier = Modifier.padding(top = 4.dp))

@Composable
fun Empty(icon: ImageVector, title: String, hint: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = AppColors.secondary, modifier = Modifier.size(32.dp))
        Text(title, style = AppType.label)
        if (hint != null) Text(hint, style = AppType.small)
    }
}
