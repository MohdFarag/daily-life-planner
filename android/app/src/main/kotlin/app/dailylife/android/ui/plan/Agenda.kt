package app.dailylife.android.ui.plan

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.components.Corner8
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.format.hm
import app.dailylife.android.ui.format.range
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel

@Composable
internal fun Agenda(vm: AppViewModel, s: Snapshot, items: List<PlanItem>) {
    val zone = s.prayer.zone
    if (items.isEmpty()) {
        Text(tr("emptyDay"), style = AppType.small)
        return
    }
    Column(Modifier.fillMaxWidth().card()) {
        items.forEachIndexed { i, b ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.border))
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .then(
                        if (b.opens)
                            Modifier.clickable {
                                vm.openPlanItem(b, b.start.atZone(s.prayer.zone).toLocalDate())
                            }
                        else Modifier
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(num(b.start.hm(zone)), style = AppType.micro, modifier = Modifier.width(44.dp))
                Box(
                    Modifier.width(4.dp)
                        .height(32.dp)
                        .clip(Corner8)
                        .background(
                            when (b.kind) {
                                PlanItemKind.TASK -> AppColors.primary
                                PlanItemKind.PRAYER -> AppColors.tint
                                PlanItemKind.EVENT -> AppColors.warningText
                            }
                        )
                )
                Column {
                    Text(
                        b.label(),
                        style =
                            AppType.label.copy(
                                color = if (b.done) AppColors.secondary else AppColors.text,
                                textDecoration = if (b.done) TextDecoration.LineThrough else null,
                            ),
                    )
                    Text(
                        num(range(b.start, b.end, zone)) +
                            if (b.needsReview) " · ${tr("needsReview")}" else "",
                        style = AppType.micro,
                    )
                }
            }
        }
    }
}
