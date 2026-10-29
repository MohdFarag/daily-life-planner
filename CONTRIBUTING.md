# Contributing

## Layout

| Path           | What                                                                                                        |
| -------------- | ----------------------------------------------------------------------------------------------------------- |
| `android/core` | Pure Kotlin domain rules (triage, scheduling, prayer engine). Separate Gradle build; no Android SDK needed. |
| `android/app`  | Android app (Compose UI, storage, background work).                                                         |
| `server/`      | Backend API (Node, Fastify, PostgreSQL). See `server/README.md`.                                            |
| `web/`         | Web prototype of the Figma design.                                                                          |

## Build and test

```bash
cd android
./gradlew -p core test ktfmtCheck                        # domain rules + formatting
./gradlew :app:ktfmtCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Kotlin is formatted with [ktfmt](https://github.com/facebook/ktfmt) in the Kotlin style; run
`./gradlew -p core ktfmtFormat` and `./gradlew :app:ktfmtFormat` before committing.
TypeScript (server and web) is formatted with Prettier (`.prettierrc.json`); run `npm run format`.

```bash
cd server && npm ci && npm run format:check && npm run lint && npm run typecheck && npm test   # needs PostgreSQL, see server/README.md
```

```bash
cd web && npm ci && npm run format:check && npm test && npm run build
```

Dependency versions live in `android/gradle/libs.versions.toml`; Dependabot proposes updates weekly.

## Workflow

Trunk-based: `main` is always releasable, and every change reaches it through a short-lived branch
and a squash-merged pull request.

- **Branch names:** `<type>/<topic>`, for example `feat/sign-in`, `fix/photo-rotation`, `ci/release-please`.
  Types: `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `build`, `ci`, `chore`.
- **PR titles follow [Conventional Commits](https://www.conventionalcommits.org):** `type(scope): summary`,
  for example `feat(android): sign in and sync`. The title becomes the squash commit on `main` and
  drives the version and changelog. `feat` bumps the minor version, `fix` the patch, and `!` or a
  `BREAKING CHANGE:` footer the major. Scopes: `android`, `core`, `server`, `web`, `ci`, `deps`.
- Keep PRs focused; stack dependent PRs (base one PR on the other's branch) instead of mixing topics.
- CI must pass before merging: formatting, lint, tests and builds for the touched projects, plus PR
  hygiene (title and branch name) and the security checks.
- Put business rules in `android/core` with unit tests; keep the app module for UI and Android integration.
- Keep each layer to one job. Android: screens render state and forward events to controllers in
  `viewmodel/`, which call `domain/` and `data/`. Server: routes handle HTTP, services hold the rules,
  repositories hold the SQL, and a module reaches another module only through its service
  (ESLint enforces this). Web prototype: screens use the store; data rules live in `state/`.
- Reference PRD requirement IDs (for example `REL02`) in code comments where a rule comes from the PRD, and in the pull request.
- New user-facing text needs both English and Arabic strings, and layouts must work right-to-left.

## Releases

Releases are automated with [release-please](https://github.com/googleapis/release-please):

1. Each merge to `main` updates an open **release PR** (`chore(main): release x.y.z`) with the next
   version and `CHANGELOG.md`, computed from the Conventional Commit titles since the last release.
2. Merging the release PR tags `vX.Y.Z` and creates the GitHub release. The release workflow then
   attaches the Android APK, AAB and `SHA256SUMS.txt`, and publishes the server image to
   `ghcr.io/mohdfarag/daily-life-planner/server` (with build provenance and an SBOM).

The version lives in `version.txt` and is mirrored into `server/package.json` and `web/package.json`.
Pushing a `v*` tag by hand rebuilds the artifacts for that tag.

The Android job runs in the `release` environment. Signing uses these secrets (set them on the
environment, or on the repository):

| Secret                      | Value                    |
| --------------------------- | ------------------------ |
| `ANDROID_KEYSTORE_BASE64`   | `base64 -w0 release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password        |
| `ANDROID_KEY_ALIAS`         | Key alias                |
| `ANDROID_KEY_PASSWORD`      | Key password             |

## Automated checks

| Workflow      | Runs on                           | What it checks                                                                         |
| ------------- | --------------------------------- | -------------------------------------------------------------------------------------- |
| Android       | PRs, `main`                       | ktfmt, core tests, app unit tests, lint, debug APK                                     |
| Server        | PRs and `main` touching `server/` | Prettier, `npm audit`, ESLint, typecheck, tests on PostgreSQL, Docker build            |
| Web prototype | PRs and `main` touching `web/`    | Prettier, `npm audit`, unit tests, build                                               |
| PR hygiene    | PRs                               | Conventional Commit title, branch name; adds area labels                               |
| Security      | PRs, `main`, weekly               | gitleaks secret scan, actionlint and zizmor on workflows, CodeQL (TypeScript, Actions) |
| Release       | `main`, `v*` tags                 | release PR, then signed artifacts and the server image                                 |

All actions are pinned to commit SHAs, workflows default to read-only tokens, and checkouts do not keep
credentials. Dependabot updates dependencies weekly (after a 7-day cooldown) with Conventional Commit titles.

## Repository settings

These are configured in the GitHub UI (Settings), not in files:

- **General → Pull Requests:** allow squash merging only, default commit message "Pull request title and
  description"; enable "Always suggest updating pull request branches", "Allow auto-merge" and
  "Automatically delete head branches".
- **Actions → General → Workflow permissions:** read-only, and enable "Allow GitHub Actions to create and
  approve pull requests" (needed for the release PR).
- **Rules → Rulesets → Protect main:** require a pull request, linear history, no force pushes or
  deletion, and these status checks: `Core tests`, `App build, lint and tests`, `Title and branch name`,
  `Secret scan (gitleaks)`, `Workflow lint (actionlint, zizmor)`. Rulesets on private repositories are
  enforced only on GitHub Pro, Team or Enterprise.
- **Environments → release:** add the signing secrets; optionally require a reviewer before release builds.
