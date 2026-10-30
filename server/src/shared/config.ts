import { z } from "zod";

const Env = z.object({
  DATABASE_URL: z.string().min(1),
  PORT: z.coerce.number().int().default(8080),
  HOST: z.string().default("0.0.0.0"),
  LOG_LEVEL: z.enum(["fatal", "error", "warn", "info", "debug", "trace", "silent"]).default("info"),
  CORS_ORIGINS: z.string().default(""),
  MEDIA_DIR: z.string().default("./data/media"),
  MAX_FILE_BYTES: z.coerce
    .number()
    .int()
    .positive()
    .default(200 * 1024 * 1024),
  USER_QUOTA_BYTES: z.coerce
    .number()
    .int()
    .positive()
    .default(2 * 1024 * 1024 * 1024),
  /** Requests per minute per client IP: whole API, and the credential endpoints (login, register, refresh). */
  API_RATE_LIMIT_PER_MINUTE: z.coerce.number().int().positive().default(300),
  AUTH_RATE_LIMIT_PER_MINUTE: z.coerce.number().int().positive().default(10),
  ACCESS_TOKEN_TTL_SECONDS: z.coerce
    .number()
    .int()
    .positive()
    .default(15 * 60),
  REFRESH_TOKEN_TTL_DAYS: z.coerce.number().int().positive().default(60),
  /** Google integration; disabled unless the client, redirect URI and secrets key are all set. */
  GOOGLE_CLIENT_ID: z.string().default(""),
  GOOGLE_CLIENT_SECRET: z.string().default(""),
  /** Public URL of GET /v1/google/callback, registered in the Google Cloud console. */
  GOOGLE_REDIRECT_URI: z.string().default(""),
  /** Where the browser goes after connecting; the Android app handles this link. */
  GOOGLE_APP_RETURN_URI: z.string().default("dailylife://google-connected"),
  GOOGLE_SYNC_INTERVAL_SECONDS: z.coerce.number().int().positive().default(300),
  /** 32 random bytes, base64: encrypts OAuth tokens at rest (SEC01). */
  SECRETS_KEY: z.string().default(""),
});

/** Whether everything the Google integration needs is configured. */
export const googleEnabled = (c: Config) => Boolean(c.GOOGLE_CLIENT_ID && c.GOOGLE_CLIENT_SECRET && c.GOOGLE_REDIRECT_URI && c.SECRETS_KEY);

export type Config = z.infer<typeof Env>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const parsed = Env.safeParse(env);
  if (!parsed.success) {
    const problems = parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
    throw new Error(`Invalid configuration: ${problems}`);
  }
  return parsed.data;
}
