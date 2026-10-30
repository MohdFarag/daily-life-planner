# Daily Life Planner

| Folder | What | Status |
| --- | --- | --- |
| [`android/`](android/) | Android app (Kotlin, Compose) and shared domain core | P0 features — see [android/README.md](android/README.md) |
| [`server/`](server/) | Backend API: accounts, sync, media, Google Tasks and Calendar, MCP (Node, Fastify, PostgreSQL) | P0 features — see [server/README.md](server/README.md) |
| [`web/`](web/) | Clickable web prototype of the Figma design (Vite + React) | Prototype, local storage only |

Product requirements: Daily Life Planning App PRD v0.1. Contributing, checks and releases:
[CONTRIBUTING.md](CONTRIBUTING.md).

## Going live

Code for all P0 requirements is in place; these need accounts or infrastructure:

1. **Server hosting:** run the image `ghcr.io/mohdfarag/daily-life-planner/server` (published by the
   release workflow) with PostgreSQL 16 and HTTPS, configured from `server/.env.example`.
2. **Google:** create a Google Cloud project, enable the Tasks and Calendar APIs, create an OAuth client
   of type "Web application" with `https://<server>/v1/google/callback` as redirect URI, and set
   `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` and `SECRETS_KEY`. Until the
   consent screen is verified, add test users in the console.
3. **App:** set the `API_SERVER_URL` repository variable so release builds pre-fill the server, and
   the signing secrets in the `release` environment.
