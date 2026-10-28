package app.dailylife.android.data

import app.dailylife.core.model.BlockPolicy
import app.dailylife.core.model.TaskList
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.prayer.PrayerSettings
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Madhab
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

data class CityPreset(val name: String, val nameAr: String, val lat: Double, val lng: Double, val zone: String, val method: CalculationMethod, val weekend: Set<DayOfWeek>)

private val FRI_SAT = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
private val SAT_SUN = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** Cities offered before location access; users can also enter coordinates (PRY02/PRY03). */
val CITIES = listOf(
    CityPreset("Cairo", "القاهرة", 30.0444, 31.2357, "Africa/Cairo", CalculationMethod.EGYPTIAN, FRI_SAT),
    CityPreset("Alexandria", "الإسكندرية", 31.2001, 29.9187, "Africa/Cairo", CalculationMethod.EGYPTIAN, FRI_SAT),
    CityPreset("Riyadh", "الرياض", 24.7136, 46.6753, "Asia/Riyadh", CalculationMethod.UMM_AL_QURA, FRI_SAT),
    CityPreset("Makkah", "مكة المكرمة", 21.3891, 39.8579, "Asia/Riyadh", CalculationMethod.UMM_AL_QURA, FRI_SAT),
    CityPreset("Dubai", "دبي", 25.2048, 55.2708, "Asia/Dubai", CalculationMethod.DUBAI, SAT_SUN),
    CityPreset("Doha", "الدوحة", 25.2854, 51.5310, "Asia/Qatar", CalculationMethod.QATAR, FRI_SAT),
    CityPreset("Kuwait City", "الكويت", 29.3759, 47.9774, "Asia/Kuwait", CalculationMethod.KUWAIT, FRI_SAT),
    CityPreset("Amman", "عمّان", 31.9454, 35.9284, "Asia/Amman", CalculationMethod.MUSLIM_WORLD_LEAGUE, FRI_SAT),
    CityPreset("Istanbul", "إسطنبول", 41.0082, 28.9784, "Europe/Istanbul", CalculationMethod.TURKEY, SAT_SUN),
    CityPreset("Karachi", "كراتشي", 24.8607, 67.0011, "Asia/Karachi", CalculationMethod.KARACHI, SAT_SUN),
    CityPreset("Kuala Lumpur", "كوالالمبور", 3.1390, 101.6869, "Asia/Kuala_Lumpur", CalculationMethod.SINGAPORE, SAT_SUN),
    CityPreset("London", "لندن", 51.5072, -0.1276, "Europe/London", CalculationMethod.MOON_SIGHTING_COMMITTEE, SAT_SUN),
    CityPreset("New York", "نيويورك", 40.7128, -74.0060, "America/New_York", CalculationMethod.NORTH_AMERICA, SAT_SUN),
)

fun CityPreset.settings() = PrayerSettings(lat, lng, ZoneId.of(zone), method, Madhab.SHAFI, label = name)

fun defaultCity(): CityPreset = CITIES.firstOrNull { it.zone == ZoneId.systemDefault().id } ?: CITIES.first()

const val WORK_LIST = "work"
const val PERSONAL_LIST = "personal"

fun defaultLists() = listOf(TaskList(WORK_LIST, "Work"), TaskList(PERSONAL_LIST, "Personal"))

fun defaultBlocks(weekend: Set<DayOfWeek>) = listOf(
    TimeBlock("block-work", "Work", LocalTime.of(9, 0), LocalTime.of(12, 0), DayOfWeek.entries.toSet() - weekend, BlockPolicy.GUIDING, setOf(WORK_LIST)),
    TimeBlock("block-family", "Family", LocalTime.of(19, 30), LocalTime.of(21, 0), DayOfWeek.entries.toSet(), BlockPolicy.PROTECTED, setOf(PERSONAL_LIST), 0xFFF7EDDA),
)
