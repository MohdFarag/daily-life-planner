package app.dailylife.android.ui.format

import app.dailylife.android.data.CITIES
import app.dailylife.android.data.PERSONAL_LIST
import app.dailylife.android.data.Snapshot
import app.dailylife.android.data.WORK_LIST
import app.dailylife.android.ui.i18n.localizeDigits
import app.dailylife.android.ui.i18n.translate
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation
import app.dailylife.core.model.TaskList
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.prayer.PrayerSettings

fun kindKey(kind: CaptureKind) =
    when (kind) {
        CaptureKind.TEXT -> "noteKind"
        CaptureKind.LIST -> "listKind"
        CaptureKind.LINK -> "linkKind"
        CaptureKind.AUDIO -> "voiceKind"
        CaptureKind.IMAGE -> "imageKind"
        CaptureKind.VIDEO -> "videoKind"
        CaptureKind.FILE -> "fileKind"
    }

fun relationKey(relation: Relation) =
    when (relation) {
        Relation.STARTS_AFTER -> "startsAfter"
        Relation.STARTS_BEFORE -> "startsBefore"
        Relation.ENDS_BEFORE -> "endsBefore"
    }

fun anchorKey(point: PrayerPoint) =
    when (point) {
        PrayerPoint.ADHAN -> "adhan"
        PrayerPoint.IQAMA -> "iqama"
        PrayerPoint.END -> "periodEnd"
    }

private val PRAYER_AR =
    mapOf(
        PrayerName.FAJR to "الفجر",
        PrayerName.DHUHR to "الظهر",
        PrayerName.ASR to "العصر",
        PrayerName.MAGHRIB to "المغرب",
        PrayerName.ISHA to "العشاء",
    )

fun prayerName(p: PrayerName, lang: String) =
    if (lang == "ar") PRAYER_AR.getValue(p)
    else p.name.lowercase().replaceFirstChar { it.uppercase() }

fun sourceLabel(service: String): String? =
    when {
        service == "app" -> null
        service == "keep" -> "Keep"
        service == "gmail" -> "Gmail"
        service == "youtube" -> "YouTube"
        service.startsWith("share:") ->
            service.removePrefix("share:").substringAfterLast('.').replaceFirstChar {
                it.uppercase()
            }
        else -> null
    }

// Built-in names are stored in English; show them translated until the user renames them.
private val BUILT_IN_NAMES =
    mapOf(
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
fun listName(s: Snapshot, listId: String): String? =
    s.lists.firstOrNull { it.id == listId }?.let { listLabel(it, s.lang) }

fun cityLabel(p: PrayerSettings, lang: String): String =
    CITIES.firstOrNull { it.name == p.label }?.let { if (lang == "ar") it.nameAr else it.name }
        ?: p.label.ifEmpty { localizeDigits("%.2f, %.2f".format(p.latitude, p.longitude), lang) }

/** What to call an attachment: its original file name, else its kind ("Photo", "Voice note"). */
fun attachmentLabel(a: Attachment, lang: String): String =
    a.name
        ?: translate(
            lang,
            when {
                a.mimeType.startsWith("image") -> "imageKind"
                a.mimeType.startsWith("video") -> "videoKind"
                a.mimeType.startsWith("audio") -> "voiceKind"
                else -> "fileKind"
            },
        )

/**
 * An item's display title. Captures saved before titles were generated used the stored file name (a
 * UUID); show the attachment's label for those instead.
 */
fun itemTitle(item: CaptureItem, lang: String): String =
    item.attachments
        .firstOrNull { java.io.File(it.localPath).name == item.title }
        ?.let { attachmentLabel(it, lang) } ?: item.title
