/** An error with an HTTP status and a stable machine-readable code. */
export class ApiError extends Error {
  constructor(
    readonly statusCode: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

// One helper per status, so call sites read as the outcome rather than a number.
export const badRequest = (code: string, message: string) => new ApiError(400, code, message);
export const unauthorized = (message = "Authentication required", code = "unauthorized") => new ApiError(401, code, message);
export const forbidden = (code: string, message: string) => new ApiError(403, code, message);
export const notFound = (what = "Resource") => new ApiError(404, "not_found", `${what} not found`);
export const conflict = (code: string, message: string) => new ApiError(409, code, message);
export const payloadTooLarge = (code: string, message: string) => new ApiError(413, code, message);
export const unprocessable = (code: string, message: string) => new ApiError(422, code, message);
export const tooManyRequests = (message: string) => new ApiError(429, "rate_limited", message);
