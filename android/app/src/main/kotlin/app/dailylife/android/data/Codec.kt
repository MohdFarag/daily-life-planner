package app.dailylife.android.data

import app.dailylife.core.focus.EntryKind
import app.dailylife.core.focus.FocusSession
import app.dailylife.core.focus.FocusState
import app.dailylife.core.focus.TimeEntry
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.BlockPolicy
import app.dailylife.core.model.CalendarEvent
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.CaptureState
import app.dailylife.core.model.ListEntry
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.SourceRef
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskList
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TaskStatus
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.model.TimingRule
import app.dailylife.core.model.UploadState
import app.dailylife.core.model.WaitingFor
import app.dailylife.core.prayer.EndRule
import app.dailylife.core.prayer.IqamaRule
import app.dailylife.core.prayer.PrayerSettings
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Madhab
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** JSON encoding of core models for local storage and export (SEC02). */
object Codec {
    private fun JSONObject.str(k: String): String? =
        if (has(k) && !isNull(k)) getString(k) else null

    private fun JSONObject.inst(k: String): Instant? = str(k)?.let(Instant::parse)

    private fun JSONObject.date(k: String): LocalDate? = str(k)?.let(LocalDate::parse)

    private fun JSONObject.intOrNull(k: String): Int? =
        if (has(k) && !isNull(k)) getInt(k) else null

    private fun JSONObject.longOrNull(k: String): Long? =
        if (has(k) && !isNull(k)) getLong(k) else null

    private fun <T> JSONArray?.map(f: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    // Capture items
    fun encode(i: CaptureItem): JSONObject =
        JSONObject()
            .put("id", i.id)
            .put("kind", i.kind.name)
            .put("title", i.title)
            .put("body", i.body)
            .put("url", i.url)
            .put(
                "source",
                JSONObject()
                    .put("service", i.source.service)
                    .put("externalId", i.source.externalId)
                    .put("url", i.source.url)
                    .put("account", i.source.account),
            )
            .put("state", i.state.name)
            .put("capturedAt", i.capturedAt.toString())
            .put(
                "attachments",
                JSONArray(
                    i.attachments.map {
                        JSONObject()
                            .put("id", it.id)
                            .put("localPath", it.localPath)
                            .put("mimeType", it.mimeType)
                            .put("sizeBytes", it.sizeBytes)
                            .put("durationMs", it.durationMs)
                            .put("uploadState", it.uploadState.name)
                            .put("name", it.name)
                            .put("mediaId", it.mediaId)
                    }
                ),
            )
            .put(
                "entries",
                JSONArray(
                    i.entries.map {
                        JSONObject()
                            .put("id", it.id)
                            .put("text", it.text)
                            .put("resolvedAs", it.resolvedAs)
                    }
                ),
            )
            .put("parentId", i.parentId)
            .put("derivedTaskIds", JSONArray(i.derivedTaskIds))
            .put(
                "waiting",
                i.waiting?.let {
                    JSONObject()
                        .put("who", it.who)
                        .put("expected", it.expected)
                        .put("requestedOn", it.requestedOn.toString())
                        .put("followUpOn", it.followUpOn?.toString())
                },
            )
            .put("reviewOn", i.reviewOn?.toString())
            .put("completedAt", i.completedAt?.toString())
            .put("trashedAt", i.trashedAt?.toString())
            .put("purgeAt", i.purgeAt?.toString())
            .put("stateBeforeTrash", i.stateBeforeTrash?.name)

    fun item(o: JSONObject): CaptureItem {
        val src = o.getJSONObject("source")
        return CaptureItem(
            id = o.getString("id"),
            kind = CaptureKind.valueOf(o.getString("kind")),
            title = o.getString("title"),
            body = o.optString("body"),
            url = o.str("url"),
            source =
                SourceRef(
                    src.getString("service"),
                    src.str("externalId"),
                    src.str("url"),
                    src.str("account"),
                ),
            state = CaptureState.valueOf(o.getString("state")),
            capturedAt = Instant.parse(o.getString("capturedAt")),
            attachments =
                o.optJSONArray("attachments").map {
                    Attachment(
                        it.getString("id"),
                        // Absent on records from another device until the file is downloaded.
                        it.optString("localPath", ""),
                        it.getString("mimeType"),
                        it.getLong("sizeBytes"),
                        it.longOrNull("durationMs"),
                        UploadState.valueOf(it.getString("uploadState")),
                        it.str("name"),
                        it.str("mediaId"),
                    )
                },
            entries =
                o.optJSONArray("entries").map {
                    ListEntry(it.getString("id"), it.getString("text"), it.str("resolvedAs"))
                },
            parentId = o.str("parentId"),
            derivedTaskIds = o.optJSONArray("derivedTaskIds").strings(),
            waiting =
                o.optJSONObject("waiting")?.let {
                    WaitingFor(
                        it.getString("who"),
                        it.str("expected"),
                        LocalDate.parse(it.getString("requestedOn")),
                        it.date("followUpOn"),
                    )
                },
            reviewOn = o.date("reviewOn"),
            completedAt = o.inst("completedAt"),
            trashedAt = o.inst("trashedAt"),
            purgeAt = o.inst("purgeAt"),
            stateBeforeTrash = o.str("stateBeforeTrash")?.let(CaptureState::valueOf),
        )
    }

    // Tasks
    fun encode(t: Task): JSONObject =
        JSONObject()
            .put("id", t.id)
            .put("title", t.title)
            .put("notes", t.notes)
            .put("listId", t.listId)
            .put("tags", JSONArray(t.tags.toList()))
            .put("important", t.important)
            .put("status", t.status.name)
            .put("estimateMin", t.estimateMin)
            .put("deadline", t.deadline?.toString())
            .put("targetDate", t.targetDate?.toString())
            .put("sourceItemId", t.sourceItemId)
            .put("createdAt", t.createdAt.toString())
            .put("completedAt", t.completedAt?.toString())
            .put("parentId", t.parentId)
            .put("position", t.position)
            .put("keepUrl", t.keepUrl)
            .put("googleUrl", t.googleUrl)

    fun task(o: JSONObject) =
        Task(
            id = o.getString("id"),
            title = o.getString("title"),
            notes = o.optString("notes"),
            listId = o.getString("listId"),
            tags = o.optJSONArray("tags").strings().toSet(),
            important = o.optBoolean("important"),
            status = TaskStatus.valueOf(o.getString("status")),
            estimateMin = o.intOrNull("estimateMin"),
            deadline = o.date("deadline"),
            targetDate = o.date("targetDate"),
            sourceItemId = o.str("sourceItemId"),
            createdAt = Instant.parse(o.getString("createdAt")),
            completedAt = o.inst("completedAt"),
            parentId = o.str("parentId"),
            position = o.str("position"),
            keepUrl = o.str("keepUrl"),
            googleUrl = o.str("googleUrl"),
        )

    fun encode(l: TaskList): JSONObject = JSONObject().put("id", l.id).put("name", l.name)

    fun list(o: JSONObject) = TaskList(o.getString("id"), o.getString("name"))

    // Rules and sessions
    fun encode(r: RelativeRule): JSONObject =
        JSONObject()
            .put("id", r.id)
            .put("prayer", r.prayer.name)
            .put("anchor", r.anchor.name)
            .put("relation", r.relation.name)
            .put("offsetMin", r.offsetMin)
            .put("durationMin", r.durationMin)
            .put("days", r.days?.let { d -> JSONArray(d.map { it.name }) })
            .put("from", r.from.toString())
            .put("until", r.until?.toString())

    fun rule(o: JSONObject) =
        RelativeRule(
            id = o.getString("id"),
            prayer = PrayerName.valueOf(o.getString("prayer")),
            anchor = PrayerPoint.valueOf(o.getString("anchor")),
            relation = Relation.valueOf(o.getString("relation")),
            offsetMin = o.getInt("offsetMin"),
            durationMin = o.getInt("durationMin"),
            days = o.optJSONArray("days")?.strings()?.map(DayOfWeek::valueOf)?.toSet(),
            from = LocalDate.parse(o.getString("from")),
            until = o.date("until"),
        )

    fun encode(s: TaskSession): JSONObject =
        JSONObject()
            .put("id", s.id)
            .put("taskId", s.taskId)
            .put("start", s.start.toString())
            .put("end", s.end.toString())
            .put("rule", (s.timing as? TimingRule.Relative)?.rule?.let(::encode))
            .put("occurrenceDate", s.occurrenceDate?.toString())
            .put("pinned", s.pinned)
            .put("state", s.state.name)
            .put("blockId", s.blockId)
            .put("googleEventId", s.googleEventId)

    fun session(o: JSONObject) =
        TaskSession(
            id = o.getString("id"),
            taskId = o.getString("taskId"),
            start = Instant.parse(o.getString("start")),
            end = Instant.parse(o.getString("end")),
            timing =
                o.optJSONObject("rule")?.let { TimingRule.Relative(rule(it)) } ?: TimingRule.Fixed,
            occurrenceDate = o.date("occurrenceDate"),
            pinned = o.optBoolean("pinned"),
            state = SessionState.valueOf(o.getString("state")),
            blockId = o.str("blockId"),
            googleEventId = o.str("googleEventId"),
        )

    fun encode(b: TimeBlock): JSONObject =
        JSONObject()
            .put("id", b.id)
            .put("name", b.name)
            .put("start", b.start.toString())
            .put("end", b.end.toString())
            .put("days", JSONArray(b.days.map { it.name }))
            .put("policy", b.policy.name)
            .put("allowedListIds", JSONArray(b.allowedListIds.toList()))
            .put("color", b.color)

    fun block(o: JSONObject) =
        TimeBlock(
            id = o.getString("id"),
            name = o.getString("name"),
            start = LocalTime.parse(o.getString("start")),
            end = LocalTime.parse(o.getString("end")),
            days = o.optJSONArray("days").strings().map(DayOfWeek::valueOf).toSet(),
            policy = BlockPolicy.valueOf(o.getString("policy")),
            allowedListIds = o.optJSONArray("allowedListIds").strings().toSet(),
            color = o.optLong("color", 0xFFE6F0E7),
        )

    fun encode(e: CalendarEvent): JSONObject =
        JSONObject()
            .put("id", e.id)
            .put("title", e.title)
            .put("start", e.start.toString())
            .put("end", e.end.toString())
            .put("calendarId", e.calendarId)
            .put("readOnly", e.readOnly)
            .put("allDay", e.allDay)
            .put("location", e.location)
            .put("description", e.description)
            .put("googleId", e.googleId)

    fun event(o: JSONObject) =
        CalendarEvent(
            o.getString("id"),
            o.getString("title"),
            Instant.parse(o.getString("start")),
            Instant.parse(o.getString("end")),
            o.getString("calendarId"),
            o.optBoolean("readOnly"),
            o.optBoolean("allDay"),
            o.str("location"),
            o.str("description"),
            o.str("googleId"),
        )

    // Rule bindings
    fun encode(b: RuleBinding): JSONObject =
        JSONObject().put("taskId", b.taskId).put("rule", encode(b.rule))

    fun ruleBinding(o: JSONObject) =
        RuleBinding(o.getString("taskId"), rule(o.getJSONObject("rule")))

    // Google calendars (written by the server; visibility and default chosen on the device)
    fun encode(c: CalendarSettings): JSONObject =
        JSONObject()
            .put(
                "list",
                JSONArray(
                    c.list.map {
                        JSONObject()
                            .put("id", it.id)
                            .put("summary", it.summary)
                            .put("primary", it.primary)
                            .put("accessRole", it.accessRole)
                            .put("visible", it.visible)
                            .put("appRole", it.appRole)
                            .put("color", it.color)
                    }
                ),
            )
            .put("defaultCalendarId", c.defaultCalendarId)

    fun calendars(o: JSONObject) =
        CalendarSettings(
            o.optJSONArray("list").map {
                CalendarEntry(
                    it.getString("id"),
                    it.optString("summary"),
                    it.optBoolean("primary"),
                    it.optString("accessRole"),
                    it.optBoolean("visible"),
                    it.str("appRole"),
                    it.str("color"),
                )
            },
            o.str("defaultCalendarId"),
        )

    // Preferences
    fun encode(p: Prefs): JSONObject =
        JSONObject()
            .put("lang", p.lang)
            .put("protectPrayers", p.protectPrayers)
            .put("prayerConfirmed", p.prayerConfirmed)
            .put("reminderMinutes", p.reminderMinutes)
            .put("windowDays", p.windowDays)

    fun prefs(o: JSONObject) =
        Prefs(
            lang = o.optString("lang", "en"),
            protectPrayers = o.optBoolean("protectPrayers", true),
            prayerConfirmed = o.optBoolean("prayerConfirmed"),
            reminderMinutes = o.optInt("reminderMinutes", 10),
            windowDays = o.optInt("windowDays", 30),
        )

    // Focus timer and time log
    fun encode(entries: List<TimeEntry>): JSONArray =
        JSONArray(
            entries.map {
                JSONObject()
                    .put("taskId", it.taskId)
                    .put("kind", it.kind.name)
                    .put("start", it.start.toString())
                    .put("end", it.end.toString())
            }
        )

    fun timeEntries(a: JSONArray?): List<TimeEntry> = a.map {
        TimeEntry(
            it.str("taskId"),
            EntryKind.valueOf(it.getString("kind")),
            Instant.parse(it.getString("start")),
            Instant.parse(it.getString("end")),
        )
    }

    fun encode(f: FocusSession): JSONObject =
        JSONObject()
            .put("taskId", f.taskId)
            .put("planned", f.planned.seconds)
            .put("state", f.state.name)
            .put("segmentStart", f.segmentStart?.toString())
            .put("entries", encode(f.entries))

    fun focus(o: JSONObject) =
        FocusSession(
            taskId = o.str("taskId"),
            planned = Duration.ofSeconds(o.getLong("planned")),
            state = FocusState.valueOf(o.getString("state")),
            segmentStart = o.inst("segmentStart"),
            entries = timeEntries(o.optJSONArray("entries")),
        )

    // Prayer settings
    fun encode(p: PrayerSettings): JSONObject =
        JSONObject()
            .put("latitude", p.latitude)
            .put("longitude", p.longitude)
            .put("zone", p.zone.id)
            .put("method", p.method.name)
            .put("madhab", p.madhab.name)
            .put("adjustments", JSONObject(p.adjustmentsMin.mapKeys { it.key.name }))
            .put(
                "iqama",
                JSONObject(
                    p.iqama
                        .map { (k, v) ->
                            k.name to
                                when (v) {
                                    is IqamaRule.AfterAdhan -> "+${v.minutes}"
                                    is IqamaRule.At -> v.time.toString()
                                }
                        }
                        .toMap()
                ),
            )
            .put(
                "end",
                JSONObject(
                    p.end
                        .map { (k, v) ->
                            k.name to
                                when (v) {
                                    is EndRule.AfterIqama -> "+${v.minutes}"
                                    is EndRule.At -> v.time.toString()
                                }
                        }
                        .toMap()
                ),
            )
            .put("fridayExtraMin", p.fridayExtraMin)
            .put("label", p.label)

    fun prayer(o: JSONObject): PrayerSettings {
        fun rules(key: String) =
            o.optJSONObject(key)?.let { j ->
                j.keys().asSequence().associate { PrayerName.valueOf(it) to j.getString(it) }
            } ?: emptyMap()
        val adj = o.optJSONObject("adjustments")
        return PrayerSettings(
            latitude = o.getDouble("latitude"),
            longitude = o.getDouble("longitude"),
            zone = ZoneId.of(o.getString("zone")),
            method = CalculationMethod.valueOf(o.getString("method")),
            madhab = Madhab.valueOf(o.getString("madhab")),
            adjustmentsMin =
                adj?.keys()?.asSequence()?.associate { PrayerName.valueOf(it) to adj.getInt(it) }
                    ?: emptyMap(),
            iqama =
                rules("iqama").mapValues { (_, v) ->
                    if (v.startsWith("+")) IqamaRule.AfterAdhan(v.drop(1).toInt())
                    else IqamaRule.At(LocalTime.parse(v))
                },
            end =
                rules("end").mapValues { (_, v) ->
                    if (v.startsWith("+")) EndRule.AfterIqama(v.drop(1).toInt())
                    else EndRule.At(LocalTime.parse(v))
                },
            fridayExtraMin = o.optInt("fridayExtraMin", 30),
            label = o.optString("label"),
        )
    }
}
