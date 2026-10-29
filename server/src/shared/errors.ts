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

export const unauthorized = (message = "Authentication required") => new ApiError(401, "unauthorized", message);
export const notFound = (what = "Resource") => new ApiError(404, "not_found", `${what} not found`);
export const conflict = (code: string, message: string) => new ApiError(409, code, message);
export const badRequest = (code: string, message: string) => new ApiError(400, code, message);
