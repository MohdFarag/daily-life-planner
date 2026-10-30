import { createHash, randomBytes } from "node:crypto";
import { GoogleApiError } from "./google.api.js";

/** Tasks, Calendar (to create the app's calendars and read the user's), and who the account is. */
export const SCOPES = ["openid", "email", "https://www.googleapis.com/auth/tasks", "https://www.googleapis.com/auth/calendar"];

const AUTHORIZE = "https://accounts.google.com/o/oauth2/v2/auth";
const TOKEN = "https://oauth2.googleapis.com/token";
const REVOKE = "https://oauth2.googleapis.com/revoke";

export interface GoogleTokens {
  accessToken: string;
  expiresAt: Date;
  /** Only returned by the first exchange (offline access). */
  refreshToken?: string;
  scope: string;
  /** From the ID token of the first exchange. */
  sub?: string;
  email?: string;
}

export interface OAuthSettings {
  clientId: string;
  clientSecret: string;
  redirectUri: string;
}

/** A fresh PKCE pair (RFC 7636, S256). */
export function pkce(): { verifier: string; challenge: string } {
  const verifier = randomBytes(32).toString("base64url");
  return { verifier, challenge: createHash("sha256").update(verifier).digest("base64url") };
}

/** OAuth 2.0 authorization code flow with PKCE against Google, for a confidential web client. */
export class GoogleOAuth {
  constructor(
    private readonly settings: OAuthSettings,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  authorizationUrl(state: string, codeChallenge: string): string {
    const params = new URLSearchParams({
      client_id: this.settings.clientId,
      redirect_uri: this.settings.redirectUri,
      response_type: "code",
      scope: SCOPES.join(" "),
      access_type: "offline",
      prompt: "consent", // always returns a refresh token
      include_granted_scopes: "true",
      state,
      code_challenge: codeChallenge,
      code_challenge_method: "S256",
    });
    return `${AUTHORIZE}?${params.toString()}`;
  }

  exchange(code: string, codeVerifier: string): Promise<GoogleTokens> {
    return this.token({ grant_type: "authorization_code", code, code_verifier: codeVerifier, redirect_uri: this.settings.redirectUri });
  }

  refresh(refreshToken: string): Promise<GoogleTokens> {
    return this.token({ grant_type: "refresh_token", refresh_token: refreshToken });
  }

  /** Revokes the grant at Google (best effort: an already revoked token is fine). */
  async revoke(token: string): Promise<void> {
    await this.fetchImpl(REVOKE, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ token }).toString(),
    });
  }

  private async token(params: Record<string, string>): Promise<GoogleTokens> {
    const res = await this.fetchImpl(TOKEN, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ ...params, client_id: this.settings.clientId, client_secret: this.settings.clientSecret }).toString(),
    });
    const body = (await res.json()) as Record<string, unknown>;
    if (!res.ok) throw new GoogleApiError(res.status, typeof body.error === "string" ? body.error : "token request failed");
    const claims = typeof body.id_token === "string" ? idTokenClaims(body.id_token) : {};
    return {
      accessToken: String(body.access_token),
      expiresAt: new Date(Date.now() + Number(body.expires_in ?? 3600) * 1000),
      refreshToken: typeof body.refresh_token === "string" ? body.refresh_token : undefined,
      scope: typeof body.scope === "string" ? body.scope : "",
      sub: claims.sub,
      email: claims.email,
    };
  }
}

/**
 * Reads the ID token's claims. It came straight from Google's token endpoint over TLS in exchange
 * for our code and secret, so its signature need not be checked (OpenID Connect Core 3.1.3.7).
 */
function idTokenClaims(idToken: string): { sub?: string; email?: string } {
  const payload = idToken.split(".")[1];
  if (!payload) return {};
  const claims = JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as Record<string, unknown>;
  return {
    sub: typeof claims.sub === "string" ? claims.sub : undefined,
    email: typeof claims.email === "string" ? claims.email : undefined,
  };
}
