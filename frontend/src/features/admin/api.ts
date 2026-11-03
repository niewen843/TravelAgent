import { api } from '../../shared/api/client'
import type { Account } from '../auth/types'
export type AuditEvent = {
  id: number
  actorUserId: number | null
  targetUserId: number | null
  action: string
  result: string
  traceId: string
  createdAt: string
}
export type PasswordReset = {
  userId: number
  temporaryPassword: string
  expiresAt: string
}
export const adminApi = {
  // 写请求由共享 API 层获取 CSRF token；调用方不将返回值写入 TanStack Query 缓存。
  resetPassword: (id: number, adminPassword: string) =>
    api<PasswordReset>(`/admin/users/${id}/password-reset`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ adminPassword }),
    }),
  users: (afterId = 0) =>
    api<Account[]>(`/admin/users?afterId=${afterId}&limit=30`),
  user: (id: number) => api<Account>(`/admin/users/${id}`),
  events: (targetUserId?: number, beforeId?: number) =>
    api<AuditEvent[]>(
      `/admin/audit-events?limit=30${targetUserId ? `&targetUserId=${targetUserId}` : ''}${beforeId ? `&beforeId=${beforeId}` : ''}`,
    ),
}
