package app.dailylife.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    gap: Dp = 16.dp,
    bottomPadding: Dp = 104.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier,
        contentPadding =
            PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = bottomPadding),
    ) {
        item { Column(verticalArrangement = Arrangement.spacedBy(gap)) { content() } }
    }
}

@Composable
fun PageHeading(title: String, subtitle: String, onSearch: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = AppType.h1)
            if (onSearch != null) AppIconButton(Icons.Outlined.Search, tr("search"), onSearch)
        }
        Text(subtitle, style = AppType.small)
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = { Spacer(Modifier.size(48.dp)) },
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, tr("back"), onBack)
        Text(title, style = AppType.label, color = AppColors.secondary)
        trailing()
    }
}
