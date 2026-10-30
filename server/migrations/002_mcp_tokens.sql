-- Personal access tokens for MCP clients (MCP03). They are valid only for this API (never Google
-- tokens), expire, can be revoked one by one, and only a SHA-256 digest is stored.
CREATE TABLE mcp_tokens (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         text NOT NULL,
    token_hash   bytea NOT NULL UNIQUE,
    created_at   timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz NOT NULL,
    last_used_at timestamptz,
    revoked_at   timestamptz
);
CREATE INDEX mcp_tokens_user_idx ON mcp_tokens (user_id);
