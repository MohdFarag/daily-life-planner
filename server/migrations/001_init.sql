-- Accounts --------------------------------------------------------------------------------------
CREATE TABLE users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email         text NOT NULL,
    password_hash text NOT NULL,
    display_name  text,
    locale        text NOT NULL DEFAULT 'en',
    -- Per-user change counter; incremented under a row lock so versions commit in order (SYN01).
    sync_version  bigint NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_email_key ON users (lower(email));

-- One row per signed-in device. Refresh tokens rotate; reuse of a rotated token revokes the session.
CREATE TABLE sessions (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_name        text,
    access_hash        bytea NOT NULL UNIQUE,
    access_expires_at  timestamptz NOT NULL,
    refresh_hash       bytea NOT NULL UNIQUE,
    refresh_expires_at timestamptz NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    last_used_at       timestamptz NOT NULL DEFAULT now(),
    revoked_at         timestamptz
);
CREATE INDEX sessions_user_idx ON sessions (user_id);

-- Refresh tokens that were rotated away; presenting one again signals theft.
CREATE TABLE retired_refresh_tokens (
    refresh_hash bytea PRIMARY KEY,
    session_id   uuid NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    retired_at   timestamptz NOT NULL DEFAULT now()
);

-- Synchronized records -------------------------------------------------------------------------
-- Every entity (items, tasks, sessions, rules, ...) is a JSON document with per-field versions,
-- so concurrent edits to different fields merge and same-field edits are reported (SYN04).
CREATE TABLE records (
    user_id        uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    entity         text NOT NULL,
    id             text NOT NULL,
    doc            jsonb NOT NULL DEFAULT '{}'::jsonb,
    field_versions jsonb NOT NULL DEFAULT '{}'::jsonb,
    version        bigint NOT NULL,
    -- Tombstone: content removed, marker kept so an older copy cannot resurrect it (DEL03).
    deleted        boolean NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, entity, id)
);
CREATE INDEX records_user_version_idx ON records (user_id, version);
CREATE INDEX records_trash_idx ON records (user_id) WHERE entity = 'items' AND doc ->> 'state' = 'TRASHED';

-- Results of applied mutations, keyed by the client's idempotency key.
CREATE TABLE mutations (
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mutation_id text NOT NULL,
    result      jsonb NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, mutation_id)
);

-- Media ----------------------------------------------------------------------------------------
CREATE TABLE media (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    filename       text,
    mime_type      text NOT NULL,
    size_bytes     bigint NOT NULL CHECK (size_bytes > 0),
    sha256         text NOT NULL,
    received_bytes bigint NOT NULL DEFAULT 0,
    status         text NOT NULL DEFAULT 'uploading' CHECK (status IN ('uploading', 'complete')),
    created_at     timestamptz NOT NULL DEFAULT now(),
    completed_at   timestamptz
);
CREATE INDEX media_user_idx ON media (user_id);
