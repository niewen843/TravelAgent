export class ApiError extends Error {
  readonly status: number
  readonly code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST'
  body?: BodyInit
  headers?: Record<string, string>
}

/** Cookie credentials stay in the browser; CSRF tokens and private responses never enter persistent storage. */
export async function api<T>(
  path: string,
  options: RequestOptions = {},
  retryCsrf = true,
): Promise<T> {
  const headers = { ...options.headers }
  if (options.method === 'POST') {
    // Obtain a current masked token for each write, including after login or logout rotates the session.
    const csrf = await api<{ headerName: string; token: string }>('/auth/csrf')
    headers[csrf.headerName] = csrf.token
  }
  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      ...options,
      headers,
      credentials: 'same-origin',
      cache: 'no-store',
      signal: AbortSignal.timeout(15000),
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '连接失败，请检查网络后重试')
  }
  if (!response.ok) {
    const error = await response
      .json()
      .catch(() => ({
        code: 'SERVER_ERROR',
        message: '服务暂时不可用，请稍后重试',
      }))
    // CSRF rejection happens before business execution, so only this specific write error is safe to retry.
    if (error.code === 'CSRF_INVALID' && retryCsrf)
      return api<T>(path, options, false)
    if (
      response.status === 401 &&
      path !== '/auth/login' &&
      path !== '/auth/me'
    ) {
      window.dispatchEvent(new Event('auth:expired'))
    }
    throw new ApiError(response.status, error.code, error.message)
  }
  return response.status === 204
    ? (undefined as T)
    : (response.json() as Promise<T>)
}
