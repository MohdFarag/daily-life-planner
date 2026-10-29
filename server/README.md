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
  jobs/retention.ts        periodic trash purge and housekeeping, run through the owning services
migrations/                plain SQL, applied in order at startup
```

The layering is enforced by ESLint: services, repositories and shared code cannot import the HTTP
framework or layer; repositories cannot import services; a service uses only its own module's
repository (plus `users`) and reaches other modules through their services; jobs never run SQL.

## API (v1)

| Area | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`; `GET /me`; `GET /sessions`; `DELETE /sessions/{id}` |
| Sync | `POST /sync/push`, `GET /sync/pull?since=&limit=` |
| Media | `POST /media`, `GET /media`, `GET /media/{id}`, `PUT /media/{id}/content`, `GET /media/{id}/content`, `DELETE /media/{id}` |
| Account | `GET /account/export`, `DELETE /account` |

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

### Retention

An hourly job purges inbox items in trash after their `purgeAt` date (tombstone + media deletion),
and removes old idempotency records, dead sessions and abandoned uploads.

## Configuration

See `.env.example`. All settings are validated at startup.

## Not yet included

Google Tasks/Calendar integration and the MCP server build on these services in the next slices.
