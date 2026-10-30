package app.dailylife.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import app.dailylife.core.model.CaptureKind

fun kindIcon(kind: CaptureKind): ImageVector =
    when (kind) {
        CaptureKind.TEXT -> Icons.Outlined.TextFields
        CaptureKind.LIST -> Icons.Outlined.Checklist
        CaptureKind.LINK -> Icons.Outlined.Link
        CaptureKind.AUDIO -> Icons.Outlined.Mic
        CaptureKind.IMAGE -> Icons.Outlined.Image
        CaptureKind.VIDEO -> Icons.Outlined.Videocam
        CaptureKind.FILE -> Icons.Outlined.Description
    }
