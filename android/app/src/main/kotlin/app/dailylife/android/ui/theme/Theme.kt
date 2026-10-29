package app.dailylife.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Design tokens from the Figma "Daily Life" variables. */
object AppColors {
    val canvas = Color(0xFFF7F8F3)
    val surface = Color(0xFFFFFFFF)
    val border = Color(0xFFDFE5DD)
    val text = Color(0xFF1E2D26)
    val secondary = Color(0xFF69756E)
    val primary = Color(0xFF225C46)
    val onPrimary = Color(0xFFFFFFFF)
    val subtle = Color(0xFFEEF1EB)
    val tint = Color(0xFFE6F0E7)
    val highlight = Color(0xFFD2EDB3)
    val dark = Color(0xFF103D2D)
    val warning = Color(0xFFF7EDDA)
    val warningText = Color(0xFF89621F)
    val danger = Color(0xFF9A3B2A)
    val now = Color(0xFFC0522F)
    val scrim = Color(0x6B0F2117)
}

/** Text styles named after the Figma styles. */
object AppType {
    val h1 =
        TextStyle(
            fontSize = 32.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.text,
        )
    val h2 =
        TextStyle(
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.text,
        )
    val h3 =
        TextStyle(
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.text,
        )
    val body =
        TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.text,
        )
    val button = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val label =
        TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.text,
        )
    val small =
        TextStyle(
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Normal,
            color = AppColors.secondary,
        )
    val micro =
        TextStyle(
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.secondary,
        )
    val timer =
        TextStyle(
            fontSize = 64.sp,
            lineHeight = 80.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.text,
        )
}

@Composable
fun PlannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = AppColors.primary,
                onPrimary = AppColors.onPrimary,
                background = AppColors.canvas,
                surface = AppColors.surface,
                onSurface = AppColors.text,
                onBackground = AppColors.text,
                secondary = AppColors.secondary,
                outline = AppColors.border,
                surfaceVariant = AppColors.subtle,
                primaryContainer = AppColors.tint,
                onPrimaryContainer = AppColors.primary,
            ),
        typography =
            Typography(
                bodyLarge = AppType.body,
                bodyMedium = AppType.small,
                labelLarge = AppType.label,
                titleLarge = AppType.h3,
            ),
        content = content,
    )
}
