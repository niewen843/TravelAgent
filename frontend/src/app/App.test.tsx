import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { authApi } from '../features/auth/api'
import { ApiError } from '../shared/api/client'
import type { Account } from '../features/auth/types'

vi.mock('../features/auth/api', () => ({
  authApi: {
    me: vi.fn(),
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    password: vi.fn(),
  },
}))
vi.mock('../features/admin/api', () => ({
  adminApi: {
    users: vi.fn().mockResolvedValue([]),
    events: vi.fn().mockResolvedValue([]),
    user: vi.fn(),
  },
}))
const account: Account = {
  id: 42,
  username: 'traveler',
  nickname: '旅人',
  role: 'USER',
  status: 'ACTIVE',
  mustChangePassword: false,
  createdAt: '2026-09-30T00:00:00',
  lastLoginAt: null,
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(authApi.me).mockRejectedValue(
    new ApiError(401, 'UNAUTHENTICATED', '请先登录'),
  )
  window.history.replaceState(null, '', '/login')
})
afterEach(cleanup)

describe('auth user flows', () => {
  it('supports password visibility, login errors, then successful login and logout', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.login)
      .mockRejectedValueOnce(
        new ApiError(401, 'INVALID_CREDENTIALS', '用户名或密码不正确'),
      )
      .mockResolvedValue(account)
    vi.mocked(authApi.logout).mockResolvedValue(undefined)
    render(<App />)
    await screen.findByRole('heading', { name: '欢迎回来' })
    await user.type(screen.getByLabelText('用户名'), 'traveler')
    await user.type(
      screen.getByLabelText('密码', { exact: true }),
      'test-password-123',
    )
    await user.click(screen.getByRole('button', { name: '显示密码' }))
    expect(screen.getByLabelText('密码', { exact: true })).toHaveAttribute(
      'type',
      'text',
    )
    await user.click(screen.getByRole('button', { name: '登录，继续计划' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '用户名或密码不正确',
    )
    await user.click(screen.getByRole('button', { name: '登录，继续计划' }))
    await screen.findByRole('heading', { name: '你好，旅人。' })
    expect(
      screen.queryByRole('link', { name: '管理后台' }),
    ).not.toBeInTheDocument()
    expect(localStorage.length).toBe(0)
    await user.click(screen.getByRole('button', { name: '退出' }))
    await screen.findByRole('heading', { name: '欢迎回来' })
  })

  it('validates confirmation and returns a registered user to login with a success notice', async () => {
    window.history.replaceState(null, '', '/register')
    vi.mocked(authApi.register).mockResolvedValue(account)
    const user = userEvent.setup()
    render(<App />)
    await screen.findByRole('heading', { name: '写下你的第一程' })
    await user.type(screen.getByLabelText('用户名'), 'traveler')
    await user.type(screen.getByLabelText('昵称'), '旅人')
    await user.type(
      screen.getByLabelText('密码', { exact: true }),
      'test-password-123',
    )
    await user.type(screen.getByLabelText('确认密码'), 'different-password')
    await user.click(screen.getByRole('button', { name: '创建账号' }))
    expect(screen.getByRole('alert')).toHaveTextContent('两次输入的密码不一致')
    expect(authApi.register).not.toHaveBeenCalled()
    await user.clear(screen.getByLabelText('确认密码'))
    await user.type(screen.getByLabelText('确认密码'), 'test-password-123')
    await user.click(screen.getByRole('button', { name: '创建账号' }))
    await screen.findByRole('heading', { name: '欢迎回来' })
    expect(screen.getByRole('status')).toHaveTextContent('账号创建成功')
    expect(screen.getByLabelText('用户名')).toHaveValue('traveler')
    expect(screen.getByLabelText('密码', { exact: true })).toHaveValue('')
  })

  it('redirects a first-login admin to password change and logs out after saving', async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      ...account,
      role: 'ADMIN',
      mustChangePassword: true,
    })
    vi.mocked(authApi.password).mockResolvedValue(undefined)
    window.history.replaceState(null, '', '/admin')
    const user = userEvent.setup()
    render(<App />)
    await screen.findByRole('heading', { name: '先设置一个新密码' })
    expect(
      screen.queryByRole('link', { name: '管理后台' }),
    ).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('当前密码'), 'current-password')
    await user.type(
      screen.getByLabelText('新密码', { exact: true }),
      'new-test-password',
    )
    await user.type(screen.getByLabelText('确认新密码'), 'new-test-password')
    await user.click(screen.getByRole('button', { name: '保存新密码' }))
    await screen.findByRole('heading', { name: '欢迎回来' })
    expect(screen.getByRole('status')).toHaveTextContent('密码已修改')
  })

  it('clears private content immediately on session expiry', async () => {
    vi.mocked(authApi.me).mockResolvedValue(account)
    render(<App />)
    await screen.findByRole('heading', { name: '你好，旅人。' })
    act(() => window.dispatchEvent(new Event('auth:expired')))
    await screen.findByRole('heading', { name: '欢迎回来' })
    expect(screen.queryByText('traveler')).not.toBeInTheDocument()
  })

  it('restores identity after reload and denies the ordinary user admin route', async () => {
    vi.mocked(authApi.me).mockResolvedValue(account)
    window.history.replaceState(null, '', '/admin')
    render(<App />)
    await screen.findByRole('heading', { name: '你好，旅人。' })
    await waitFor(() => expect(window.location.pathname).toBe('/account'))
  })
})

describe('identity request races', () => {
  it('preserves an anonymous login draft when focus returns from a password manager', async () => {
    const user = userEvent.setup()
    render(<App />)
    await screen.findByRole('heading', { name: '欢迎回来' })
    await user.type(screen.getByLabelText('用户名'), 'draft_traveler')
    act(() => window.dispatchEvent(new Event('focus')))
    await waitFor(() => expect(authApi.me).toHaveBeenCalledTimes(2))
    expect(screen.getByLabelText('用户名')).toHaveValue('draft_traveler')
  })

  it('ignores an old identity response after the session has expired', async () => {
    let resolve!: (account: Account) => void
    vi.mocked(authApi.me).mockReturnValue(
      new Promise<Account>((done) => {
        resolve = done
      }),
    )
    render(<App />)
    await waitFor(() => expect(authApi.me).toHaveBeenCalled())
    act(() => window.dispatchEvent(new Event('auth:expired')))
    await screen.findByRole('heading', { name: '欢迎回来' })
    await act(async () => {
      resolve(account)
    })
    expect(
      screen.queryByRole('heading', { name: '你好，旅人。' }),
    ).not.toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: '欢迎回来' }),
    ).toBeInTheDocument()
  })
})
