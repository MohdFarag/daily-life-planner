package app.dailylife.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.dailylife.android.data.CITIES
import app.dailylife.android.data.CityPreset
import app.dailylife.android.data.Snapshot
import app.dailylife.android.ui.components.Chip
import app.dailylife.android.ui.components.Corner12
import app.dailylife.android.ui.components.DetailRow
import app.dailylife.android.ui.components.ScreenColumn
import app.dailylife.android.ui.components.SectionLabel
import app.dailylife.android.ui.components.TopBar
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.format.prayerName
import app.dailylife.android.ui.format.range
import app.dailylife.android.ui.i18n.LocalLang
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import app.dailylife.core.model.PrayerName
import app.dailylife.core.prayer.PrayerAnchor
import app.dailylife.core.prayer.PrayerSettings
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Madhab

@Composable
fun PrayerSettingsScreen(vm: AppViewModel, s: Snapshot) {
    val settings = s.prayer
    val anchors = vm.dayPlanner.anchors(s, vm.today())
    val save: (PrayerSettings) -> Unit = { vm.settings.savePrayerSettings(it) }

    ScreenColumn {
        TopBar(tr("prayerTimes"), { vm.navigator.back() })
        CityPicker(settings) { save(PrayerSettingsEdits.withCity(settings, it)) }
        MethodPicker(settings.method) { save(settings.copy(method = it)) }
        SwitchRow(tr("asrMethod"), settings.madhab == Madhab.HANAFI) { hanafi ->
            save(settings.copy(madhab = if (hanafi) Madhab.HANAFI else Madhab.SHAFI))
        }
        SwitchRow(tr("protectPrayers"), s.prefs.protectPrayers) {
            vm.settings.savePrefs(s.prefs.copy(protectPrayers = it))
        }
        PrayerName.entries.forEach { prayer ->
            PrayerCard(prayer, settings, anchors.firstOrNull { it.prayer == prayer }, save)
        }
        DetailRow(tr("fridayExtra"), num(settings.fridayExtraMin)) {
            save(PrayerSettingsEdits.nextFridayExtra(settings))
        }
        if (anchors.isEmpty())
            Text(tr("prayerSettingsInvalid"), style = AppType.label, color = AppColors.warningText)
        Text(tr("methodNote") + " · ${settings.zone.id}", style = AppType.micro)
    }
}

@Composable
private fun CityPicker(settings: PrayerSettings, onPick: (CityPreset) -> Unit) {
    val lang = LocalLang.current
    SectionLabel(tr("city"))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CITIES.forEach { city ->
            Chip(
                if (lang == "ar") city.nameAr else city.name,
                settings.label == city.name,
                { onPick(city) },
            )
        }
    }
}

@Composable
private fun MethodPicker(selected: CalculationMethod, onPick: (CalculationMethod) -> Unit) {
    SectionLabel(tr("method"))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CalculationMethod.entries
            .filter { it != CalculationMethod.OTHER }
            .forEach { method ->
                val name =
                    method.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                Chip(name, selected == method, { onPick(method) })
            }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppType.body, modifier = Modifier.weight(1f))
        Switch(
            checked,
            onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AppColors.primary),
        )
    }
}

/** One prayer's adhan adjustment, iqama and end of period (PRY01). Tapping a value steps it. */
@Composable
private fun PrayerCard(
    prayer: PrayerName,
    settings: PrayerSettings,
    anchor: PrayerAnchor?,
    save: (PrayerSettings) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(Corner12).background(AppColors.tint).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                prayerName(prayer, LocalLang.current),
                style = AppType.label,
                color = AppColors.primary,
            )
            if (anchor != null)
                Text(num(range(anchor.adhan, anchor.end, settings.zone)), style = AppType.micro)
        }
        DetailRow(tr("adjust"), num(settings.adjustmentsMin[prayer] ?: 0)) {
            save(PrayerSettingsEdits.nextAdjustment(settings, prayer))
        }
        DetailRow(tr("iqamaAfter"), num(PrayerSettingsEdits.iqamaMinutes(settings, prayer))) {
            save(PrayerSettingsEdits.nextIqama(settings, prayer))
        }
        DetailRow(tr("endAfter"), num(PrayerSettingsEdits.endMinutes(settings, prayer))) {
            save(PrayerSettingsEdits.nextEnd(settings, prayer))
        }
    }
}
