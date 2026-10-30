import type { Db } from "../../shared/db.js";
import { notFound, unauthorized } from "../../shared/errors.js";
import { digest, newToken } from "../../shared/tokens.js";
import { mcpRepository as repo, type McpTokenRow } from "./mcp.repository.js";

/** Recognisable prefix, so a leaked token can be found by secret scanners. */
const PREFIX = "dlp_mcp_";
const TTL_DAYS = 180;

export interface McpTokenInfo {
  id: string;
  name: string;
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
}

const toInfo = (t: McpTokenRow): McpTokenInfo => ({
  id: t.id,
  name: t.name,
  createdAt: t.createdAt.toISOString(),
  expiresAt: t.expiresAt.toISOString(),
  lastUsedAt: t.lastUsedAt?.toISOString() ?? null,
});

/**
 * Access tokens an assistant uses to reach the MCP endpoint (MCP03). They belong to this API only,
 * are shown once, expire after 180 days and can be revoked individually.
 */
export class McpTokenService {
  constructor(private readonly db: Db) {}

  async create(userId: string, name: string): Promise<McpTokenInfo & { token: string }> {
    const token = PREFIX + newToken();
    const row = await repo.insert(this.db, userId, name, digest(token), TTL_DAYS);
    return { ...toInfo(row), token };
  }

  async list(userId: string): Promise<McpTokenInfo[]> {
    return (await repo.listActive(this.db, userId)).map(toInfo);
  }

  async revoke(userId: string, id: string): Promise<void> {
    if (!(await repo.revoke(this.db, userId, id))) throw notFound("Token");
  }

  /** The user a bearer token acts for. */
  async authenticate(token: string): Promise<string> {
    const owner = token.startsWith(PREFIX) ? await repo.touch(this.db, digest(token)) : undefined;
    if (!owner) throw unauthorized("MCP token is invalid, expired or revoked");
    return owner.userId;
  }
}
