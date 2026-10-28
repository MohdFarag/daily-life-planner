package app.dailylife.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

val UTC: ZoneId = ZoneId.of("UTC")
fun at(date: LocalDate, hhmm: String, zone: ZoneId = UTC): Instant = date.atTime(LocalTime.parse(hhmm)).atZone(zone).toInstant()
fun local(i: Instant, zone: ZoneId = UTC): String = i.atZone(zone).toLocalTime().toString()
