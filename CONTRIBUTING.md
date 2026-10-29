# Contributing

## Layout

| Path | What |
| --- | --- |
| `android/core` | Pure Kotlin domain rules (triage, scheduling, prayer engine). Separate Gradle build; no Android SDK needed. |
| `android/app` | Android app (Compose UI, storage, background work). |
| `server/` | Backend API (Node, Fastify, PostgreSQL). See `server/README.md`. |
| `web/` | Web prototype of the Figma design. |

## Build and test

```bash
cd android
./gradlew -p core test                                   # domain rules
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

```bash
cd server && npm ci && npm run lint && npm run typecheck && npm test   # needs PostgreSQL, see server/README.md
```

```bash
cd web && npm ci && npm run build
```

Dependency versions live in `android/gradle/libs.versions.toml`; Dependabot proposes updates weekly.

## Workflow

- Branch from `main` as `feature/<topic>` or `fix/<topic>`; open a pull request when ready.
- CI must pass: core tests, app unit tests, lint and the debug build.
- Put business rules in `android/core` with unit tests; keep the app module for UI and Android integration.
- Reference PRD requirement IDs (for example `REL02`) in code comments where a rule comes from the PRD, and in the pull request.
- New user-facing text needs both English and Arabic strings, and layouts must work right-to-left.

## Releases

Tag `vX.Y.Z` on `main`. The release workflow builds the APK and AAB and creates a GitHub release.
Signing needs these repository secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |
