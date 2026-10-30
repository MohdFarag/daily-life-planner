package app.dailylife.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType

@Composable
fun MenuRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = AppType.body.copy(color = if (danger) AppColors.danger else AppColors.text),
        modifier =
            Modifier.fillMaxWidth()
                .clip(Corner12)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
fun Field(value: String, placeholder: String, minHeight: Int = 0, onChange: (String) -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth()
            .heightIn(min = maxOf(minHeight, 48).dp)
            .card(Corner12)
            .padding(12.dp)
    ) {
        if (value.isEmpty())
            Text(placeholder, style = AppType.body.copy(color = AppColors.secondary))
        BasicTextField(
            value,
            onChange,
            textStyle = AppType.body,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
