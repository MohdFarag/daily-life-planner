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
| 09 More | `src/screens/More.tsx` (plus Tasks, Collections, Trash, Prayer times, Calendar, Preferences, Search) |

Design tokens (colors, radii, spacing, type styles) mirror the Figma variables in `src/styles.css`.

## Data

Everything is stored on the device: app state in `localStorage`, voice/photo/file captures in IndexedDB.
Export data (More) downloads a JSON copy. Prayer times and the "Google" calendar are illustrative
sample data — no location service or Google account is connected.
