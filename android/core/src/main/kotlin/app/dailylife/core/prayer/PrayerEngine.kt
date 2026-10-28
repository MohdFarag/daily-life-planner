package app.dailylife.core.prayer

import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.HighLatitudeRule
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Iqama is either an offset from adhan or a fixed local time (PRY02). */
sealed interface IqamaRule {
    data class AfterAdhan(val minutes: Int) : IqamaRule
    data class At(val time: LocalTime) : IqamaRule
}

/** The end of the prayer period is either a duration after iqama or a fixed local time (PRY02). */
sealed interface EndRule {
    data class AfterIqama(val minutes: Int) : EndRule
    data class At(val time: LocalTime) : EndRule
}

data class PrayerSettings(
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
    val method: CalculationMethod = CalculationMethod.EGYPTIAN,
    val madhab: Madhab = Madhab.SHAFI,
    val highLatitudeRule: HighLatitudeRule? = null,
    val adjustmentsMin: Map<PrayerName, Int> = emptyMap(),
    val iqama: Map<PrayerName, IqamaRule> = PrayerName.entries.associateWith { IqamaRule.AfterAdhan(15) },
    val end: Map<PrayerName, EndRule> = PrayerName.entries.associateWith { EndRule.AfterIqama(15) },
    /** Extra minutes added to the Dhuhr period on Fridays for Jumu'ah (PRY03). */
    val fridayExtraMin: Int = 30,
    val label: String = "",
)

/** A prayer's three times on one local date (PRY01). */
data class PrayerAnchor(
    val prayer: PrayerName,
    val date: LocalDate,
    val adhan: Instant,
    val iqama: Instant,
    val end: Instant,
) {
    fun at(point: PrayerPoint): Instant = when (point) {
        PrayerPoint.ADHAN -> adhan
        PrayerPoint.IQAMA -> iqama
        PrayerPoint.END -> end
    }
}

class InvalidPrayerSettings(message: String) : IllegalArgumentException(message)

/** Supplies calculated adhan times. Separated so tests and mosque timetables can plug in. */
fun interface AdhanSource {
    fun adhan(date: LocalDate, settings: PrayerSettings): Map<PrayerName, Instant>
}

/** Astronomical calculation via the Adhan library; works offline. */
object AstronomicalAdhanSource : AdhanSource {
    override fun adhan(date: LocalDate, settings: PrayerSettings): Map<PrayerName, Instant> {
        val adj = settings.adjustmentsMin
        val params = settings.method.parameters.copy(
            madhab = settings.madhab,
            highLatitudeRule = settings.highLatitudeRule ?: settings.method.parameters.highLatitudeRule,
            prayerAdjustments = PrayerAdjustments(
                fajr = adj[PrayerName.FAJR] ?: 0,
                dhuhr = adj[PrayerName.DHUHR] ?: 0,
                asr = adj[PrayerName.ASR] ?: 0,
                maghrib = adj[PrayerName.MAGHRIB] ?: 0,
                isha = adj[PrayerName.ISHA] ?: 0,
            ),
        )
        val times = PrayerTimes(
            Coordinates(settings.latitude, settings.longitude),
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            params,
        )
        fun conv(i: kotlinx.datetime.Instant) = Instant.ofEpochSecond(i.epochSeconds, i.nanosecondsOfSecond.toLong())
        return mapOf(
            PrayerName.FAJR to conv(times.fajr),
            PrayerName.DHUHR to conv(times.dhuhr),
            PrayerName.ASR to conv(times.asr),
            PrayerName.MAGHRIB to conv(times.maghrib),
            PrayerName.ISHA to conv(times.isha),
        )
    }
}

class PrayerEngine(private val source: AdhanSource = AstronomicalAdhanSource) {

    /** Anchors for one local date. Fixed local times are resolved through the zone, so DST applies once. */
    fun anchors(date: LocalDate, settings: PrayerSettings): List<PrayerAnchor> {
        val adhan = source.adhan(date, settings)
        return PrayerName.entries.map { p ->
            val a = adhan.getValue(p)
            val iqama = when (val r = settings.iqama[p] ?: IqamaRule.AfterAdhan(0)) {
                is IqamaRule.AfterAdhan -> a.plusSeconds(r.minutes * 60L)
                is IqamaRule.At -> date.atTime(r.time).atZone(settings.zone).toInstant()
            }
            var end = when (val r = settings.end[p] ?: EndRule.AfterIqama(0)) {
                is EndRule.AfterIqama -> iqama.plusSeconds(r.minutes * 60L)
                is EndRule.At -> date.atTime(r.time).atZone(settings.zone).toInstant()
            }
            if (p == PrayerName.DHUHR && date.dayOfWeek == DayOfWeek.FRIDAY) end = end.plusSeconds(settings.fridayExtraMin * 60L)
            if (iqama.isBefore(a)) throw InvalidPrayerSettings("$p iqama ($iqama) is before adhan ($a) on $date")
            if (end.isBefore(iqama)) throw InvalidPrayerSettings("$p end ($end) is before iqama ($iqama) on $date")
            PrayerAnchor(p, date, a, iqama, end)
        }
    }

    /** Returns a list of problems with the settings for the given date; empty when valid. */
    fun validate(date: LocalDate, settings: PrayerSettings): List<String> =
        try { anchors(date, settings); emptyList() } catch (e: InvalidPrayerSettings) { listOf(e.message ?: "Invalid settings") }
}
