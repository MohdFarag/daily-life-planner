package app.dailylife.android.ui.settings

import app.dailylife.android.data.CityPreset
import app.dailylife.android.data.settings
import app.dailylife.core.model.PrayerName
import app.dailylife.core.prayer.EndRule
import app.dailylife.core.prayer.IqamaRule
import app.dailylife.core.prayer.PrayerSettings

/** Changes offered on the prayer settings screen (PRY02). Pure: each returns new settings. */
object PrayerSettingsEdits {
    private val OFFSET_STEPS = listOf(0, 5, 10, 15, 20, 25, 30, 45)
    private const val DEFAULT_OFFSET = 15
    private const val MAX_ADJUSTMENT = 5
    private const val FRIDAY_STEP = 15
    private const val FRIDAY_MAX = 60

    /** A new city keeps the user's own iqama, end-of-period, adjustment and madhab choices. */
    fun withCity(current: PrayerSettings, city: CityPreset): PrayerSettings =
        city
            .settings()
            .copy(
                iqama = current.iqama,
                end = current.end,
                adjustmentsMin = current.adjustmentsMin,
                madhab = current.madhab,
                fridayExtraMin = current.fridayExtraMin,
            )

    fun iqamaMinutes(settings: PrayerSettings, prayer: PrayerName): Int =
        (settings.iqama[prayer] as? IqamaRule.AfterAdhan)?.minutes ?: DEFAULT_OFFSET

    fun endMinutes(settings: PrayerSettings, prayer: PrayerName): Int =
        (settings.end[prayer] as? EndRule.AfterIqama)?.minutes ?: DEFAULT_OFFSET

    fun nextIqama(settings: PrayerSettings, prayer: PrayerName): PrayerSettings =
        settings.copy(
            iqama =
                settings.iqama +
                    (prayer to IqamaRule.AfterAdhan(nextStep(iqamaMinutes(settings, prayer))))
        )

    fun nextEnd(settings: PrayerSettings, prayer: PrayerName): PrayerSettings =
        settings.copy(
            end =
                settings.end +
                    (prayer to EndRule.AfterIqama(nextStep(endMinutes(settings, prayer))))
        )

    /** Steps the calculated time by one minute, wrapping from +5 back to −5. */
    fun nextAdjustment(settings: PrayerSettings, prayer: PrayerName): PrayerSettings {
        val next = (settings.adjustmentsMin[prayer] ?: 0) + 1
        return settings.copy(
            adjustmentsMin =
                settings.adjustmentsMin +
                    (prayer to if (next > MAX_ADJUSTMENT) -MAX_ADJUSTMENT else next)
        )
    }

    fun nextFridayExtra(settings: PrayerSettings): PrayerSettings =
        settings.copy(
            fridayExtraMin = (settings.fridayExtraMin + FRIDAY_STEP) % (FRIDAY_MAX + FRIDAY_STEP)
        )

    private fun nextStep(current: Int) =
        OFFSET_STEPS[(OFFSET_STEPS.indexOf(current) + 1) % OFFSET_STEPS.size]
}
