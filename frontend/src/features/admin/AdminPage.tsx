import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../auth/auth-context'
import { AccountLayout } from '../../shared/ui/AccountLayout'
import type { Account } from '../auth/types'
import { adminApi } from './api'
import { ResetPasswordPanel } from './ResetPasswordPanel'

const actions: Record<string, string> = {
  ADMIN_RESET_PASSWORD: '重置用户密码',
  REGISTER: '注册账号',
  LOGIN: '登录',
  LOGOUT: '退出登录',
  PASSWORD_CHANGED: '修改密码',
  ADMIN_BOOTSTRAP: '创建管理员',
  ADMIN_LIST_USERS: '查看用户列表',
  ADMIN_VIEW_USER: '查看用户',
  ADMIN_VIEW_AUDIT: '查看审计',
  ACCESS_DENIED: '访问被拒绝',
}
function localTime(time: string) {
  return new Date(`${time}Z`).toLocaleString('zh-CN', { hour12: false })
}

export function AdminPage() {
  const { account } = useAuth()
  const [afterId, setAfterId] = useState(0)
  const [selected, setSelected] = useState<Account | null>(null)
  const [beforeId, setBeforeId] = useState<number | undefined>()
  const [error, setError] = useState('')
  const [lookupId, setLookupId] = useState('')
  const [resetUser, setResetUser] = useState<Account | null>(null)
  const userQuery = useQuery({
    queryKey: ['admin-users', account?.id, afterId],
    queryFn: () => adminApi.users(afterId),
    refetchOnWindowFocus: false,
  })
  const eventQuery = useQuery({
    queryKey: ['admin-events', account?.id, selected?.id, beforeId],
    queryFn: () => adminApi.events(selected?.id, beforeId),
    refetchOnWindowFocus: false,
  })
  const users = userQuery.data ?? []
  const events = eventQuery.data ?? []
  const loadingUsers = userQuery.isFetching
  const loadingEvents = eventQuery.isFetching
  const message = error || userQuery.error?.message || eventQuery.error?.message
  async function selectUser(id: number) {
    setError('')
    setResetUser(null)
    try {
      setSelected(await adminApi.user(id))
      setBeforeId(undefined)
    } catch (cause) {
      setError((cause as Error).message)
    }
  }
  return (
    <AccountLayout>
      <div className="page-heading">
        <div>
          <h1>用户与访问记录</h1>
          <p>按用户查看账号和审计信息，定位登录与权限问题。</p>
        </div>
        <button
          className="button secondary"
          onClick={() => {
            setError('')
            void userQuery.refetch()
            void eventQuery.refetch()
          }}
        >
          刷新
        </button>
      </div>
      {message && (
        <p className="message error" role="alert">
          {message}
        </p>
      )}
      <section className="admin-section">
        <div className="section-heading">
          <h2>用户账号</h2>
          <span>支持按 ID 定位普通用户并重置密码</span>
        </div>
        <form
          className="user-lookup"
          onSubmit={(event) => {
            event.preventDefault()
            const id = Number(lookupId)
            if (Number.isSafeInteger(id) && id > 0) void selectUser(id)
            else setError('请输入有效的用户 ID')
          }}
        >
          <label htmlFor="lookup-user-id">
            用户 ID
            <input
              id="lookup-user-id"
              type="number"
              min="1"
              step="1"
              required
              value={lookupId}
              onChange={(event) => setLookupId(event.target.value)}
            />
          </label>
          <button className="button secondary" type="submit">
            定位用户
          </button>
        </form>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>用户 ID</th>
                <th>用户名</th>
                <th>昵称</th>
                <th>类型</th>
                <th>状态</th>
                <th>最近登录</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td>{user.id}</td>
                  <td>{user.username}</td>
                  <td>{user.nickname}</td>
                  <td>{user.role === 'ADMIN' ? '管理员' : '用户'}</td>
                  <td>
                    {user.status !== 'ACTIVE'
                      ? '已禁用'
                      : user.mustChangePassword
                        ? '待修改密码'
                        : '正常'}
                  </td>
                  <td>
                    {user.lastLoginAt
                      ? localTime(user.lastLoginAt)
                      : '尚未登录'}
                  </td>
                  <td className="user-actions">
                    <button
                      className="text-button"
                      onClick={() => void selectUser(user.id)}
                    >
                      查看记录
                    </button>
                    {user.role === 'USER' && user.status === 'ACTIVE' && (
                      <button
                        className="text-button"
                        onClick={() => setResetUser(user)}
                      >
                        重置密码
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {loadingUsers && <p role="status">正在加载账号…</p>}
        {!loadingUsers && users.length === 0 && <p>暂无更多用户。</p>}
        <div className="pagination">
          <button
            className="text-button"
            disabled={afterId === 0 || loadingUsers}
            onClick={() => setAfterId(0)}
          >
            回到首页
          </button>
          <button
            className="text-button"
            disabled={users.length < 30 || loadingUsers}
            onClick={() => setAfterId(users.at(-1)!.id)}
          >
            下一页
          </button>
        </div>
      </section>
      {resetUser && (
        <ResetPasswordPanel
          key={resetUser.id}
          user={resetUser}
          onClose={() => setResetUser(null)}
          onReset={() => {
            setSelected((current) =>
              current?.id === resetUser.id
                ? { ...current, mustChangePassword: true }
                : current,
            )
            setBeforeId(undefined)
            void userQuery.refetch()
            void eventQuery.refetch()
          }}
        />
      )}
      <section className="admin-section">
        <div className="section-heading">
          <h2>
            {selected ? `${selected.nickname}的访问记录` : '最近访问记录'}
          </h2>
          {selected && (
            <button
              className="text-button"
              onClick={() => {
                setSelected(null)
                setBeforeId(undefined)
              }}
            >
              查看全部
            </button>
          )}
        </div>
        {selected && (
          <div className="selected-user">
            <p>
              用户 ID {selected.id}，用户名 {selected.username}，昵称{' '}
              {selected.nickname}，
              {selected.role === 'ADMIN' ? '管理员' : '普通用户'}，
              {selected.status !== 'ACTIVE'
                ? '已禁用'
                : selected.mustChangePassword
                  ? '待修改密码'
                  : '正常'}
            </p>
            {selected.role === 'USER' && selected.status === 'ACTIVE' && (
              <button
                className="text-button"
                onClick={() => setResetUser(selected)}
              >
                重置此用户密码
              </button>
            )}
          </div>
        )}
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>时间</th>
                <th>操作</th>
                <th>操作者 ID</th>
                <th>目标用户 ID</th>
                <th>结果</th>
                <th>请求追踪</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id}>
                  <td>{localTime(event.createdAt)}</td>
                  <td>{actions[event.action] ?? event.action}</td>
                  <td>
                    {event.actorUserId == null ? (
                      '未登录'
                    ) : (
                      <button
                        className="text-button"
                        aria-label={`查看用户 ${event.actorUserId}`}
                        onClick={() => void selectUser(event.actorUserId!)}
                      >
                        {event.actorUserId}
                      </button>
                    )}
                  </td>
                  <td>
                    {event.targetUserId == null ? (
                      '—'
                    ) : (
                      <button
                        className="text-button"
                        aria-label={`查看用户 ${event.targetUserId}`}
                        onClick={() => void selectUser(event.targetUserId!)}
                      >
                        {event.targetUserId}
                      </button>
                    )}
                  </td>
                  <td>
                    {
                      (
                        {
                          SUCCESS: '成功',
                          DENIED: '拒绝',
                          FAILED: '失败',
                        } as Record<string, string>
                      )[event.result]
                    }
                  </td>
                  <td className="trace-cell">{event.traceId}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {loadingEvents && <p role="status">正在加载记录…</p>}
        {!loadingEvents && events.length === 0 && <p>暂无相关访问记录。</p>}
        <div className="pagination">
          <button
            className="text-button"
            disabled={!beforeId || loadingEvents}
            onClick={() => setBeforeId(undefined)}
          >
            最新记录
          </button>
          <button
            className="text-button"
            disabled={events.length < 30 || loadingEvents}
            onClick={() => setBeforeId(events.at(-1)!.id)}
          >
            更早记录
          </button>
        </div>
      </section>
    </AccountLayout>
  )
}
