/**
 * Single entry point for every backend call.
 *
 * Requests use same-origin relative paths: Vite proxies /api to the gateway in dev, nginx does
 * the same in the container. That keeps CORS out of the picture entirely. VITE_API_URL is
 * available as an override for pointing a local dev server at a remote gateway.
 */
const BASE_URL = import.meta.env.VITE_API_URL ?? ''

const TOKEN_KEY = 'pesowise.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** Shape of the error body every service returns via its ApiExceptionHandler. */
export interface ApiErrorBody {
  timestamp?: string
  status?: number
  message?: string
  fieldErrors?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

/** Set by AuthProvider so a 401 anywhere can drop the session exactly once. */
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** Login and register must not trigger the session-expiry handler on a 401. */
  skipAuthRedirect?: boolean
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, skipAuthRedirect = false } = options

  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 401 && !skipAuthRedirect) {
    // The token is gone or expired. Drop it so the router bounces to /login.
    clearToken()
    onUnauthorized?.()
    throw new ApiError(401, 'Your session expired. Please sign in again.')
  }

  if (!response.ok) {
    throw new ApiError(response.status, ...(await describeFailure(response)))
  }

  // 204 and empty bodies are valid successful responses (DELETE, for instance).
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** Pulls the message and field errors out of a failure body, tolerating non-JSON responses. */
async function describeFailure(
  response: Response,
): Promise<[string, Record<string, string>]> {
  try {
    const body = (await response.json()) as ApiErrorBody
    return [body.message ?? fallbackMessage(response.status), body.fieldErrors ?? {}]
  } catch {
    // A gateway 503 or an nginx error page is HTML, not JSON.
    return [fallbackMessage(response.status), {}]
  }
}

function fallbackMessage(status: number): string {
  if (status === 404) return 'Not found.'
  if (status === 503) return 'That service is unavailable. Please try again shortly.'
  if (status >= 500) return 'Something went wrong on our end.'
  return `Request failed (${status}).`
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...opts, method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
