import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from '../features/auth/auth-context'
import { AuthPage } from '../features/auth/AuthPage'
import { AccountPage } from '../features/account/AccountPage'
import { PasswordPage } from '../features/account/PasswordPage'
import { AdminPage } from '../features/admin/AdminPage'
import { Brand } from '../shared/ui/Brand'

function Protected({
  children,
  admin = false,
}: {
  children: ReactNode
  admin?: boolean
}) {
  const { account } = useAuth()
  const location = useLocation()
  if (!account)
    return (
      <Navigate to="/login" replace state={{ returnTo: location.pathname }} />
    )
  if (account.mustChangePassword && location.pathname !== '/account/password')
    return <Navigate to="/account/password" replace />
  if (admin && account.role !== 'ADMIN')
    return <Navigate to="/account" replace />
  return <div key={account.id}>{children}</div>
}

export function AppRoutes() {
  const { loading, error, refresh } = useAuth()
  if (loading || error)
    return (
      <main className="session-loading">
        <Brand />
        <p role="status">{error || '正在打开你的旅行手册…'}</p>
        {error && (
          <button className="button primary" onClick={() => void refresh()}>
            重新连接
          </button>
        )}
      </main>
    )
  return (
    <Routes>
      <Route path="/login" element={<AuthPage key="login" mode="login" />} />
      <Route
        path="/register"
        element={<AuthPage key="register" mode="register" />}
      />
      <Route
        path="/account"
        element={
          <Protected>
            <AccountPage />
          </Protected>
        }
      />
      <Route
        path="/account/password"
        element={
          <Protected>
            <PasswordPage />
          </Protected>
        }
      />
      <Route
        path="/admin"
        element={
          <Protected admin>
            <AdminPage />
          </Protected>
        }
      />
      <Route path="*" element={<Navigate to="/account" replace />} />
    </Routes>
  )
}
