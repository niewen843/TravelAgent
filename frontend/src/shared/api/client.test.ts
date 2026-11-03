import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
const csrf = () =>
  json({ headerName: 'X-CSRF-TOKEN', token: 'masked-test-token' })
afterEach(() => vi.unstubAllGlobals())

describe('session-aware API client', () => {
  it('fetches CSRF for writes and retries only an explicit CSRF rejection', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(
        json({ code: 'CSRF_INVALID', message: '过期' }, 403),
      )
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json({ id: 1 }))
    vi.stubGlobal('fetch', fetch)
    expect(await api('/auth/register', { method: 'POST', body: '{}' })).toEqual(
      { id: 1 },
    )
    expect(fetch).toHaveBeenCalledTimes(4)
    expect(fetch.mock.calls[1][1].headers['X-CSRF-TOKEN']).toBe(
      'masked-test-token',
    )
    expect(fetch.mock.calls[1][1].credentials).toBe('same-origin')
  })
  it('does not replay a write after a server failure', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(
        json({ code: 'SERVER_ERROR', message: '失败' }, 500),
      )
    vi.stubGlobal('fetch', fetch)
    await expect(
      api('/auth/password', { method: 'POST', body: '{}' }),
    ).rejects.toMatchObject({ status: 500 })
    expect(fetch).toHaveBeenCalledTimes(2)
  })
  it('announces an expired protected request but not wrong login credentials', async () => {
    const expired = vi.fn()
    window.addEventListener('auth:expired', expired)
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(
          json({ code: 'SESSION_EXPIRED', message: '失效' }, 401),
        ),
    )
    await expect(api('/admin/users')).rejects.toMatchObject({ status: 401 })
    expect(expired).toHaveBeenCalledTimes(1)
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(csrf())
        .mockResolvedValueOnce(
          json({ code: 'INVALID_CREDENTIALS', message: '错误' }, 401),
        ),
    )
    await expect(api('/auth/login', { method: 'POST' })).rejects.toMatchObject({
      status: 401,
    })
    expect(expired).toHaveBeenCalledTimes(1)
    window.removeEventListener('auth:expired', expired)
  })
})
