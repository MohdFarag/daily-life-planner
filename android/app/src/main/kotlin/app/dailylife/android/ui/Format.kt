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
import app.dailylife.android.data.CITIES
import app.dailylife.android.data.PERSONAL_LIST
import app.dailylife.android.data.Snapshot
import app.dailylife.android.data.WORK_LIST
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.TaskList
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.prayer.PrayerSettings
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

// Built-in names are stored in English; show them translated until the user renames them.
private val BUILT_IN_NAMES = mapOf(
    WORK_LIST to ("Work" to "work"),
    PERSONAL_LIST to ("Personal" to "personal"),
    "block-work" to ("Work" to "work"),
    "block-family" to ("Family" to "family"),
)

private fun builtInName(id: String, name: String, lang: String): String =
    BUILT_IN_NAMES[id]?.takeIf { it.first == name }?.let { translate(lang, it.second) } ?: name

fun listLabel(list: TaskList, lang: String) = builtInName(list.id, list.name, lang)
fun blockLabel(block: TimeBlock, lang: String) = builtInName(block.id, block.name, lang)

/** The display name of a task's list, or null if the list no longer exists. */
fun listName(s: Snapshot, listId: String): String? = s.lists.firstOrNull { it.id == listId }?.let { listLabel(it, s.prefs.lang) }

fun cityLabel(p: PrayerSettings, lang: String): String =
    CITIES.firstOrNull { it.name == p.label }?.let { if (lang == "ar") it.nameAr else it.name }
        ?: p.label.ifEmpty { localizeDigits("%.2f, %.2f".format(p.latitude, p.longitude), lang) }

/** What to call an attachment: its original file name, else its kind ("Photo", "Voice note"). */
fun attachmentLabel(a: Attachment, lang: String): String = a.name ?: translate(lang, when {
    a.mimeType.startsWith("image") -> "imageKind"
    a.mimeType.startsWith("video") -> "videoKind"
    a.mimeType.startsWith("audio") -> "voiceKind"
    else -> "fileKind"
})

/**
 * An item's display title. Captures saved before titles were generated used the stored file name
 * (a UUID); show the attachment's label for those instead.
 */
fun itemTitle(item: CaptureItem, lang: String): String =
    item.attachments.firstOrNull { java.io.File(it.localPath).name == item.title }?.let { attachmentLabel(it, lang) } ?: item.title
