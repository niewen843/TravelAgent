import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { Brand } from './Brand'
import { useAuth } from '../../features/auth/auth-context'

export function AccountLayout({ children }: { children: ReactNode }) {
  const { account, signOut } = useAuth()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function logout() {
    setBusy(true)
    setError('')
    try {
      await signOut()
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="workspace">
      <header className="workspace-header">
        <Brand />
        <nav aria-label="账号导航">
          <NavLink to="/account" end>
            我的账号
          </NavLink>
          {account?.role === 'ADMIN' && !account.mustChangePassword && (
            <NavLink to="/admin">管理后台</NavLink>
          )}
        </nav>
        <div className="user-menu">
          <span>{account?.nickname}</span>
          <button
            className="text-button"
            disabled={busy}
            onClick={() => void logout()}
          >
            {busy ? '退出中…' : '退出'}
          </button>
        </div>
      </header>
      <main className="workspace-main">
        {error && (
          <p className="message error" role="alert">
            {error}
          </p>
        )}
        {children}
      </main>
      <footer className="workspace-footer">
        行笺 <span>每一程，都有自己的节奏。</span>
        <Link to="/account/password">修改密码</Link>
      </footer>
    </div>
  )
}
