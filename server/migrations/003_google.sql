-- Google connection (PRD §6, SEC01). Tokens are encrypted with SECRETS_KEY (AES-256-GCM) before
-- they reach the database, never exported, and deleted with the account.
CREATE TABLE google_accounts (
    user_id            uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    google_sub         text NOT NULL,
    email              text,
    scopes             text NOT NULL,
    refresh_token_enc  bytea NOT NULL,
    access_token_enc   bytea,
    access_expires_at  timestamptz,
    -- App-owned secondary calendars (BLK01): sessions and time blocks.
    sessions_calendar  text,
    blocks_calendar    text,
    connected_at       timestamptz NOT NULL DEFAULT now(),
    last_sync_at       timestamptz,
    last_error         text
);

-- Pending OAuth authorizations: PKCE verifier per state, valid for a few minutes.
CREATE TABLE google_oauth_states (
    state         text PRIMARY KEY,
    user_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_verifier text NOT NULL,
    expires_at    timestamptz NOT NULL
);

-- Which Google object mirrors which local record, and what was last exchanged, so changes are
-- sent once in each direction (no echo).
CREATE TABLE google_links (
    user_id        uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind           text NOT NULL,         -- list | task | session | block | event
    local_id       text NOT NULL,
    google_id      text NOT NULL,
    container_id   text NOT NULL,         -- task list or calendar id
    etag           text,
    local_version  bigint NOT NULL,       -- record version last sent to or received from Google
    PRIMARY KEY (user_id, kind, local_id)
);
CREATE INDEX google_links_google_idx ON google_links (user_id, kind, google_id);

-- Incremental sync positions: Tasks updatedMin per list, Calendar syncToken per calendar (SYN02, SYN03).
CREATE TABLE google_sync_state (
    user_id   uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope     text NOT NULL,
    cursor    text NOT NULL,
    PRIMARY KEY (user_id, scope)
);
