package app.dailylife.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.dailylife.android.navigation.Tab
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType

@Composable
fun BottomNav(active: Tab, onSelect: (Tab) -> Unit) {
    val items =
        listOf(
            Triple(Tab.TODAY, Icons.Outlined.WbSunny, "today"),
            Triple(Tab.INBOX, Icons.Outlined.Inbox, "inbox"),
            Triple(Tab.PLAN, Icons.Outlined.CalendarMonth, "plan"),
            Triple(Tab.MORE, Icons.Outlined.MoreHoriz, "more"),
        )
    Column(Modifier.fillMaxWidth().background(AppColors.surface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.border))
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 6.dp, vertical = 2.dp)) {
            items.forEach { (tab, icon, key) ->
                val selected = tab == active
                Column(
                    Modifier.weight(1f).height(64.dp).clickable(role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(56.dp, 28.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) AppColors.tint else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            null,
                            tint = if (selected) AppColors.primary else AppColors.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr(key),
                        style = AppType.micro,
                        color = if (selected) AppColors.primary else AppColors.secondary,
                    )
                }
            }
        }
    }
}
