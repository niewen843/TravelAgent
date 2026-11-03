import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AuthContext } from '../auth/auth-context'
import type { Account } from '../auth/types'
import { AdminPage } from './AdminPage'
import { adminApi } from './api'

vi.mock('./api', () => ({
  adminApi: {
    users: vi.fn(),
    user: vi.fn(),
    events: vi.fn(),
    resetPassword: vi.fn(),
  },
}))
const target: Account = {
  id: 42,
  username: 'traveler',
  nickname: '旅人',
  role: 'USER',
  status: 'ACTIVE',
  mustChangePassword: false,
  createdAt: '2026-09-30T00:00:00',
  lastLoginAt: null,
}
const admin: Account = {
  ...target,
  id: 1,
  username: 'admin',
  nickname: '管理员',
  role: 'ADMIN',
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(adminApi.users).mockResolvedValue([admin, target])
  vi.mocked(adminApi.user).mockResolvedValue(target)
  vi.mocked(adminApi.events).mockResolvedValue([
    {
      id: 7,
      actorUserId: 1,
      targetUserId: 42,
      action: 'ADMIN_RESET_PASSWORD',
      result: 'SUCCESS',
      traceId: 'test-trace',
      createdAt: '2026-09-30T00:00:00',
    },
  ])
})
afterEach(cleanup)
function openAdmin() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AuthContext.Provider
          value={{
            account: admin,
            loading: false,
            error: '',
            notice: '',
            refresh: vi.fn(),
            signedIn: vi.fn(),
            signOut: vi.fn(),
            passwordChanged: vi.fn(),
          }}
        >
          <AdminPage />
        </AuthContext.Provider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return client
}
it('shows user IDs, locates an ID outside the current page and navigates from audit IDs', async () => {
  const user = userEvent.setup()
  openAdmin()
  await screen.findByRole('cell', { name: 'traveler' })
  expect(
    screen.getByRole('columnheader', { name: '用户 ID' }),
  ).toBeInTheDocument()
  // 管理员行没有重置入口，普通用户行才有。
  expect(screen.getAllByRole('button', { name: '重置密码' })).toHaveLength(1)
  await user.type(screen.getByLabelText('用户 ID'), '42')
  await user.click(screen.getByRole('button', { name: '定位用户' }))
  await waitFor(() => expect(adminApi.user).toHaveBeenCalledWith(42))
  await screen.findByText(/用户名 traveler，昵称/)
  await user.click(screen.getByRole('button', { name: '查看用户 42' }))
  await waitFor(() => expect(adminApi.user).toHaveBeenCalledTimes(2))
})
it('revalidates the admin password, clears credentials on failure and only displays the result until closed', async () => {
  const user = userEvent.setup()
  const temporary = 'Temporary-test-only-29482'
  vi.mocked(adminApi.resetPassword)
    .mockRejectedValueOnce(new Error('管理员当前密码不正确'))
    .mockResolvedValue({
      userId: 42,
      temporaryPassword: temporary,
      expiresAt: new Date(Date.now() + 1800000).toISOString(),
    })
  const client = openAdmin()
  await screen.findByRole('cell', { name: 'traveler' })
  await user.click(screen.getByRole('button', { name: '重置密码' }))
  await user.type(screen.getByLabelText('管理员当前密码'), 'incorrect')
  await user.click(screen.getByRole('button', { name: '验证身份并重置密码' }))
  expect(await screen.findByRole('alert')).toHaveTextContent(
    '管理员当前密码不正确',
  )
  expect(screen.getByLabelText('管理员当前密码')).toHaveValue('')
  await user.type(
    screen.getByLabelText('管理员当前密码'),
    'admin-test-password',
  )
  await user.click(screen.getByRole('button', { name: '验证身份并重置密码' }))
  expect(await screen.findByLabelText('临时密码（仅本次展示）')).toHaveValue(
    temporary,
  )
  expect(adminApi.resetPassword).toHaveBeenLastCalledWith(
    42,
    'admin-test-password',
  )
  expect(
    JSON.stringify(
      client
        .getQueryCache()
        .getAll()
        .map((query) => query.state.data),
    ),
  ).not.toContain(temporary)
  expect(localStorage.length).toBe(0)
  expect(sessionStorage.length).toBe(0)
  await user.click(screen.getByRole('button', { name: '已保存，关闭' }))
  expect(screen.queryByDisplayValue(temporary)).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '重置密码' }))
  expect(screen.getByLabelText('管理员当前密码')).toHaveValue('')
  expect(screen.queryByDisplayValue(temporary)).not.toBeInTheDocument()
})
