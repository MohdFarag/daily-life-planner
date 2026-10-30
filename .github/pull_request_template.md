<!-- Title: Conventional Commits, e.g. "feat(android): sign in and sync". It becomes the commit on main. -->

## What changed

<!-- One or two sentences on the change and why. -->

## PRD coverage

<!-- Requirement IDs this implements or affects, e.g. CAP02, TRI03, REL01. "None" for tooling. -->

## How it was tested

<!-- Tick what applies; CI runs these for the touched projects. -->

- [ ] Android: `./gradlew -p core test ktfmtCheck` and `./gradlew :app:ktfmtCheck :app:testDebugUnitTest :app:lintDebug`
- [ ] Server: `npm run format:check && npm run lint && npm run typecheck && npm test`
- [ ] Web: `npm run format:check && npm test && npm run build`
- [ ] Tried on a device or emulator (which one?)

## Screenshots

<!-- For UI changes: before / after, including Arabic (RTL) where relevant. -->
