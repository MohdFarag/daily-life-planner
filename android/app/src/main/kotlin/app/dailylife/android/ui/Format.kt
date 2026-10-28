package app.dailylife.android.ui

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
import app.dailylife.core.model.PrayerName
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HM = DateTimeFormatter.ofPattern("HH:mm")

fun Instant.hm(zone: ZoneId): String = atZone(zone).toLocalTime().format(HM)
fun range(start: Instant, end: Instant, zone: ZoneId) = "${start.hm(zone)}–${end.hm(zone)}"

fun locale(lang: String): Locale = if (lang == "ar") Locale.forLanguageTag("ar-EG") else Locale.UK

fun dayLabel(date: LocalDate, lang: String): String =
    "${date.dayOfWeek.getDisplayName(TextStyle.FULL, locale(lang))}${if (lang == "ar") "، " else ", "}" +
        localizeDigits("${date.dayOfMonth}", lang) + " " + date.month.getDisplayName(TextStyle.FULL, locale(lang))

fun shortDate(date: LocalDate, lang: String): String =
    localizeDigits("${date.dayOfMonth}", lang) + " " + date.month.getDisplayName(TextStyle.SHORT, locale(lang))

fun duration(d: Duration, lang: String): String {
    val h = d.toHours()
    val m = d.toMinutes() % 60
    val s = if (lang == "ar") listOfNotNull(if (h > 0) "$h س" else null, if (m > 0 || h == 0L) "$m د" else null).joinToString(" ")
    else listOfNotNull(if (h > 0) "${h}h" else null, if (m > 0 || h == 0L) "${m}m" else null).joinToString(" ")
    return localizeDigits(s, lang)
}

fun clock(seconds: Long): String {
    val s = kotlin.math.abs(seconds)
    return (if (seconds < 0) "+" else "") + "%02d:%02d".format(s / 60, s % 60)
}

fun relative(at: Instant, lang: String, now: Instant = Instant.now()): String {
    val min = Duration.between(at, now).toMinutes()
    return when {
        min < 1 -> translate(lang, "justNow")
        min < 60 -> translate(lang, "minAgo", min)
        min < 60 * 24 -> translate(lang, "hoursAgo", min / 60)
        else -> translate(lang, "daysAgo", min / (60 * 24))
    }
}

fun kindIcon(kind: CaptureKind): ImageVector = when (kind) {
    CaptureKind.TEXT -> Icons.Outlined.TextFields
    CaptureKind.LIST -> Icons.Outlined.Checklist
    CaptureKind.LINK -> Icons.Outlined.Link
    CaptureKind.AUDIO -> Icons.Outlined.Mic
    CaptureKind.IMAGE -> Icons.Outlined.Image
    CaptureKind.VIDEO -> Icons.Outlined.Videocam
    CaptureKind.FILE -> Icons.Outlined.Description
}

fun kindKey(kind: CaptureKind) = when (kind) {
    CaptureKind.TEXT -> "noteKind"; CaptureKind.LIST -> "listKind"; CaptureKind.LINK -> "linkKind"; CaptureKind.AUDIO -> "voiceKind"
    CaptureKind.IMAGE -> "imageKind"; CaptureKind.VIDEO -> "videoKind"; CaptureKind.FILE -> "fileKind"
}

private val PRAYER_AR = mapOf(PrayerName.FAJR to "الفجر", PrayerName.DHUHR to "الظهر", PrayerName.ASR to "العصر", PrayerName.MAGHRIB to "المغرب", PrayerName.ISHA to "العشاء")
fun prayerName(p: PrayerName, lang: String) = if (lang == "ar") PRAYER_AR.getValue(p) else p.name.lowercase().replaceFirstChar { it.uppercase() }

fun sourceLabel(service: String): String? = when {
    service == "app" -> null
    service == "keep" -> "Keep"
    service == "gmail" -> "Gmail"
    service == "youtube" -> "YouTube"
    service.startsWith("share:") -> service.removePrefix("share:").substringAfterLast('.').replaceFirstChar { it.uppercase() }
    else -> null
}
