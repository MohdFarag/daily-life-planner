# Daily Life Planner

A mobile-first planner built from the Figma file *Daily Life Planner – Mobile UI* (Core mobile experience / v0.1).

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # type-check + production build in dist/
```

On a phone the app fills the screen; on a wider screen it renders inside a 412 × 892 device frame matching the design.

## Screens

| Figma frame | Where |
| --- | --- |
| 01 Today, 10 Today (Arabic) | `src/screens/Today.tsx` (More → Preferences → العربية switches to RTL) |
| 02 Inbox | `src/screens/Inbox.tsx` |
| 03 Quick capture | `src/screens/CaptureSheet.tsx` (+ button) |
| 04 Process an item | `src/screens/Process.tsx` |
| 05 Task detail, "Task / newly created" | `src/screens/TaskDetail.tsx` |
| 06 Plan | `src/screens/Plan.tsx` |
| 07 Schedule a session | `src/screens/ScheduleSheet.tsx` |
| 08 Focus, "Focus / paused" | `src/screens/Focus.tsx` |
| 09 More | `src/screens/more/More.tsx` (plus one file each for Tasks, Collections, Trash, Prayer times, Calendar, Preferences, Search) |

Design tokens (colors, radii, spacing, type styles) mirror the Figma variables in `src/styles.css`.

## Code layout

```
src/
  main.tsx, App.tsx   entry point; the phone frame and screen switch
  screens/            one file per screen (screens/more/ for the More tab's sub-screens)
  components/         shared UI: app chrome, controls, layout, rows, icons
  hooks/              reusable React hooks (ticker, blob URLs, item labels)
  state/              data types, sample data, persistence, pure data transforms
                      (mutations.ts, schedule.ts) and the store that wires them to React
  i18n/               English and Arabic strings, translation and digit helpers
  lib/                framework-free helpers: time math, ids, IndexedDB blobs, downloads
```

## Data

Everything is stored on the device: app state in `localStorage`, voice/photo/file captures in IndexedDB.
Export data (More) downloads a JSON copy. Prayer times and the "Google" calendar are illustrative
sample data — no location service or Google account is connected.
