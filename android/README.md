# Daily Life Planner — Android

Kotlin + Jetpack Compose app. Slice 1 of the P0 plan: offline inbox, sharing, triage, tasks,
sessions, time blocks, prayer times and prayer-relative scheduling, focus timer and local reminders.

```
android/
  core/   Pure Kotlin domain logic (no Android dependency), its own Gradle build
  app/    Android app: Compose UI, SQLite storage, share target, WorkManager jobs
```

## Build

Requires JDK 17+ and the Android SDK (API 35).

```bash
./gradlew :app:assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew -p core test                # domain tests, no Android SDK needed
./gradlew :app:testDebugUnitTest
```

CI (`.github/workflows/android.yml`) runs all of the above and uploads the debug APK as an artifact.

## What is implemented (PRD references)

| Area | Status |
| --- | --- |
| Capture: text, list, link, voice recording, photo, video, files (CAP01) | ✅ saved locally; confirmation only after the SQLite write (CAP02) |
| Drafts survive closing the app (CAP03) | ✅ |
| Lists processed entry by entry; text → list conversion (CAP04) | ✅ |
| Share target incl. Keep: text, links, single and multiple files, copied into app storage immediately (CAP05) | ✅ similar shares offer "open existing" or "keep new copy" |
| Triage: Do Now (confirm to complete), task(s), reference, someday (+review date), waiting for (+follow-up), split, trash, undo (TRI01–07) | ✅ |
| Trash with 30-day retention, restore, purge job (DEL01) | ✅ |
| Tasks: lists, tags, importance, In Progress, estimate, deadline + early target (ORG01, PAR01 basics) | ✅ |
| Sessions: several per task, fixed or prayer-relative ("starts after / starts before / ends before" adhan, iqama or end) (REL01) | ✅ |
| Rolling 30-day window, idempotent recalculation, pinned exceptions, "Needs review" on conflicts (REL02–04) | ✅ daily WorkManager job + on settings change |
| Prayer times: offline calculation (Adhan), city presets, method, Asr madhab, per-prayer adjustment, iqama, end of period, Friday extension, DST via IANA zone (PRY01–03) | ✅ coordinates entry beyond presets is not in the UI yet |
| Time blocks: guiding vs protected, allowed lists, capacity counted once (BLK03–04) | ✅ |
| Conflict checks and free-slot suggestions before saving; explicit override (PLN02, AC11) | ✅ |
| Focus: pause, break, extend, overtime; time log of work vs break (DO01, TIM01) | ✅ |
| Local session reminders and follow-up notifications (NOT02 fallback) | ✅ |
| Arabic UI and RTL | ✅ |
| JSON export (SEC02) | ✅ |

## Not in this slice

- Backend, account, server database and media upload (next slice: Node/TS + PostgreSQL)
- Google Tasks / Calendar sync and Calendar reminders (NOT01) — needs Google Cloud OAuth setup
- MCP server
- Home-screen widget, location-based prayer settings, Drive / Gmail / YouTube (P1)

The app font is the system font (Roboto / Noto Arabic); Manrope can be added as `res/font` TTF files.
