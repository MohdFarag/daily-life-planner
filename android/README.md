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
| Account: sign in or create one, sign out, delete account with password confirmation (UX03, SEC03) | ✅ More → Account & sync; tokens encrypted with an Android Keystore key |
| Sync with the server: every local change goes to an outbox in the same transaction, is pushed with an idempotency key and merged field by field; changes from other devices are pulled after (SYN01, SYN04) | ✅ per-item state in the inbox (on this device / waiting / synced / needs attention); rejected changes can be discarded |
| Media upload in resumable 1 MiB chunks with SHA-256, download on other devices; text never waits for media (CAP02) | ✅ failed uploads stay visible and keep the local copy |

## Not in this slice

- Google Tasks / Calendar sync and Calendar reminders (NOT01) — needs Google Cloud OAuth setup
- MCP server
- Home-screen widget, location-based prayer settings, Drive / Gmail / YouTube (P1)

The app font is the system font (Roboto / Noto Arabic); Manrope can be added as `res/font` TTF files.

## Syncing with a server

Run the backend from [`server/`](../server/README.md), then in the app open **More → Account & sync**.
The server address must use HTTPS; plain HTTP is accepted only for `localhost`, `127.0.0.1` and the
emulator's host `10.0.2.2` (development). Builds can pre-fill the address with `-PserverUrl=https://…`;
the release workflow uses the `API_SERVER_URL` repository variable.

`SyncEndToEndTest` drives the real client against a running server. It is skipped unless
`SYNC_SERVER_URL` is set:

```bash
SYNC_SERVER_URL=http://127.0.0.1:8080 ./gradlew :app:testDebugUnitTest --tests '*SyncEndToEndTest*'
```

