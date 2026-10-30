# Daily Life Planner — Server

Backend for accounts, multi-device sync and media. Node 22, Fastify 5, PostgreSQL 16, TypeScript.
Interactive API docs (OpenAPI) are served at `/docs`.

## Run locally

```bash
docker compose up --build        # PostgreSQL + API on http://localhost:8080
```

Or against your own PostgreSQL:

```bash
cp .env.example .env              # set DATABASE_URL
npm ci
npm run dev                       # migrates, then serves with reload
```

## Checks

```bash
npm run lint && npm run typecheck
TEST_DATABASE_URL=postgres://postgres@localhost:5432/planner_test npm test
```

Tests run against a real PostgreSQL; each test file creates and drops its own schema.

## Structure

```
src/
  server.ts, migrate.ts    entry points
  app.ts                   composition root: creates services, registers routes under /v1
  http/                    HTTP plumbing: authentication hook, error responses, health probes
  shared/                  config (validated env), database pool/transactions/migrations, domain errors
  modules/<feature>/
    *.routes.ts            HTTP only: schemas, status codes; calls the service
    *.schemas.ts           Zod request/response contracts (also feed the OpenAPI docs)
    *.service.ts           business rules and transaction boundaries; no HTTP, no SQL strings
    *.repository.ts        SQL only; maps rows to typed objects
  modules/users/           the users table, shared by auth, media (quota lock) and account deletion
  modules/planner/         planning operations on synced records (inbox, tasks, sessions, review)
  modules/mcp/             MCP tokens and the MCP endpoint; tools call the planner service
  jobs/retention.ts        periodic trash purge and housekeeping, run through the owning services
migrations/                plain SQL, applied in order at startup
```

The layering is enforced by ESLint: services, repositories and shared code cannot import the HTTP
framework or layer; repositories cannot import services; a service uses only its own module's
repository (plus `users`) and reaches other modules through their services; jobs never run SQL.

## API (v1)

| Area    | Endpoints                                                                                                                             |
| ------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Auth    | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`; `GET /me`; `GET /sessions`; `DELETE /sessions/{id}`            |
| Sync    | `POST /sync/push`, `GET /sync/pull?since=&limit=`                                                                                     |
| Media   | `POST /media`, `GET /media`, `GET /media/{id}`, `PUT /media/{id}/content`, `GET /media/{id}/content`, `DELETE /media/{id}`            |
| Account | `GET /account/export`, `DELETE /account`                                                                                              |
| Google  | `GET /google/status`, `POST /google/connect`, `GET /google/callback`, `POST /google/sync`, `POST /google/calendars`, `DELETE /google` |
| MCP     | `POST /mcp/tokens`, `GET /mcp/tokens`, `DELETE /mcp/tokens/{id}`; the MCP endpoint `POST /mcp`                                        |

### Authentication

Email and password (scrypt). Login returns a short-lived **access token** (15 min) and a **refresh
token** (60 days) per device. Refreshing rotates both; presenting an already-used refresh token is
treated as theft and signs that device out. Only SHA-256 digests of tokens are stored.

### Sync protocol (PRD SYN01, SYN04, DEL01, DEL03)

- Each user has one ordered change log. `GET /sync/pull?since=<cursor>` returns changes after the
  cursor plus the next cursor and `hasMore`.
- Devices send `POST /sync/push` with mutations, each carrying a client-generated `mutationId`.
  Retrying a mutation returns its first result, so a flaky network never applies it twice.
- An upsert sends only changed fields and the `baseVersion` the device last saw. Fields are merged
  individually: edits to different fields from different devices both survive; if another device
  changed the same field after `baseVersion`, the server keeps its value and returns a `conflict`
  with the server value for the app to show.
- Deletes leave a tombstone. A stale copy of a deleted record is rejected instead of resurrecting it.
- Entities: `items`, `tasks`, `lists`, `sessions`, `rules`, `blocks`, `settings`, `timeEntries`.

### Media (PRD CAP01, CAP02)

1. `POST /media` with size, MIME type and SHA-256 → upload id (per-file and per-account limits apply).
2. `PUT /media/{id}/content` with `Upload-Offset` and raw bytes, in chunks of up to 16 MiB.
3. After a failure, `GET /media/{id}` returns `receivedBytes`; continue from there.
4. The final chunk is verified against the SHA-256 before the upload is complete.

Bytes are stored on local disk (`MEDIA_DIR`) behind a `MediaStorage` interface, so an object store
can be added without touching the service.

### MCP (PRD MCP01–MCP04)

`POST /v1/mcp` is a [Model Context Protocol](https://modelcontextprotocol.io) server (Streamable HTTP,
stateless) that lets an assistant work with the user's planner. Tools: `get_daily_plan`, `list_inbox`,
`add_inbox_item`, `convert_item_to_tasks`, `list_tasks`, `complete_task`, `preview_session`,
`schedule_session`, `weekly_review`.

- **Same services (MCP01):** tools call `PlannerService`, which reads and writes the user's synced records
  through `SyncService`, exactly as a device does, so changes reach every device on its next pull.
- **Its own tokens (MCP03):** an assistant authenticates with an MCP token (`dlp_mcp_…`), created in the
  app or with `POST /v1/mcp/tokens`. MCP tokens are shown once, stored as SHA-256 digests, expire after
  180 days, can be revoked, and are never Google tokens. App session tokens are not accepted at `/mcp`.
- **Untrusted content (MCP04):** the server instructions and tool descriptions tell the assistant that
  titles, notes and bodies are captured data, not instructions. Write tools are annotated as such, and
  scheduling over a conflict needs an explicit `allowConflicts`.
- Prayer times and time blocks are calculated on the device; `preview_session` checks other sessions, and
  the phone re-checks prayers and blocks, marking clashes "Needs review" (REL04).

To connect an MCP client, point it at `https://<server>/v1/mcp` with the header
`Authorization: Bearer dlp_mcp_…`.

### Google Tasks and Calendar (PRD §6, §7)

Enabled when `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` and `SECRETS_KEY` are set
(see `.env.example`); otherwise the endpoints answer `503 google_not_configured`.

- **Connecting:** the app calls `POST /google/connect` and opens the returned Google consent page. Google
  redirects to `/google/callback`, which hands control back to the app (`dailylife://google-connected`).
  The flow uses PKCE and a single-use state that expires after 10 minutes, and requests offline access.
- **Tokens (SEC01):** refresh and access tokens are encrypted with AES-256-GCM (`SECRETS_KEY`), never
  sent to devices, exported or logged, revoked on disconnect, and revoked and deleted with the account.
- **Tasks (TSK01–TSK03):** lists and tasks sync both ways (title, notes, completion, subtask parent and
  position). Keep links are shown, never copied into the inbox. Deadlines and early targets stay app
  fields (NOT03, NOT04).
- **Sessions (SES01, NOT01, NOT02):** each planned session becomes an event with a popup reminder in the
  app's "Sessions" calendar, and the session records the event id. Devices stop their own reminder once
  a session is published.
- **Time blocks (BLK01, BLK03):** weekly recurring events in the app's "Time blocks" calendar; protected
  blocks show as busy, guiding blocks as free.
- **Calendars (CAL01, CAL02, SYN02):** the calendar list (with the user's visibility choices and default
  calendar) is a settings record on devices. Events of visible calendars are read incrementally with
  sync tokens into `events` records. Events created or edited on a device are written to Google. Each
  recurring occurrence is its own record, so editing one never changes the series.
- **No echo:** `google_links` keeps each object's etag and the record version last exchanged, so a
  change crosses once in each direction. A background job syncs connected accounts every
  `GOOGLE_SYNC_INTERVAL_SECONDS`; the app can sync on demand.
- **Not included:** Calendar push notifications (`events.watch`) need a verified public domain, so changes
  in Google arrive with the next sync instead.

### Retention

An hourly job purges inbox items in trash after their `purgeAt` date (tombstone + media deletion),
and removes old idempotency records, dead sessions and abandoned uploads.

## Configuration

See `.env.example`. All settings are validated at startup.

## Not yet included

Calendar push notifications from Google; see the Google section above.
