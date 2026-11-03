import { api } from '../../shared/api/client'
import type { Account, RegisterInput } from './types'

export const authApi = {
  me: () => api<Account>('/auth/me'),
  login: (username: string, password: string) =>
    api<Account>('/auth/login', {
      method: 'POST',
      body: new URLSearchParams({ username, password }),
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    }),
  register: (input: RegisterInput) =>
    api<Account>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(input),
      headers: { 'Content-Type': 'application/json' },
    }),
  logout: () => api<void>('/auth/logout', { method: 'POST' }),
  password: (currentPassword: string, newPassword: string) =>
    api<void>('/auth/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
      headers: { 'Content-Type': 'application/json' },
    }),
}
