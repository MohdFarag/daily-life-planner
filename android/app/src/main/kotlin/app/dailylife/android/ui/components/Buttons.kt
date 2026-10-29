package app.dailylife.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType

@Composable
fun AppIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AppColors.primary,
) {
    Box(
        Modifier.size(48.dp)
            .card()
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

enum class ButtonStyle {
    PRIMARY,
    SECONDARY,
    LIGHT,
    QUIET,
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val (bg, fg, border) =
        when (style) {
            ButtonStyle.PRIMARY -> Triple(AppColors.primary, AppColors.onPrimary, null)
            ButtonStyle.SECONDARY ->
                Triple(AppColors.surface, AppColors.primary, BorderStroke(1.dp, AppColors.border))
            ButtonStyle.LIGHT -> Triple(AppColors.surface, AppColors.primary, null)
            ButtonStyle.QUIET -> Triple(Color.Transparent, AppColors.primary, null)
        }
    // Full width by default; a caller's own size (applied first) still takes precedence.
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(Corner16)
            .background(if (enabled) bg else bg.copy(alpha = 0.45f))
            .then(if (border != null) Modifier.border(border, Corner16) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = AppType.button, color = if (enabled) fg else fg.copy(alpha = 0.6f))
    }
}
