import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { PasswordField } from '../../shared/ui/PasswordField'
import { authApi } from './api'
import { useAuth } from './auth-context'
import { AuthLayout } from './components/AuthLayout'

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const register = mode === 'register'
  const { account, signedIn, notice: authNotice } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState(location.state?.username ?? '')
  const [nickname, setNickname] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const feedback =
    notice ||
    authNotice ||
    (location.state?.registered ? '账号创建成功，请登录。' : '')
  const destination = ['/account', '/admin'].includes(location.state?.returnTo)
    ? location.state.returnTo
    : '/account'
  if (account)
    return (
      <Navigate
        to={account.mustChangePassword ? '/account/password' : destination}
        replace
      />
    )
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setError('')
    if (register && password !== confirm) {
      setError('两次输入的密码不一致')
      return
    }
    if (
      register &&
      (password.length < 10 || new TextEncoder().encode(password).length > 72)
    ) {
      setError('密码至少 10 位，且不能超过 72 个 UTF-8 字节')
      return
    }
    setBusy(true)
    try {
      if (register) {
        await authApi.register({
          username: username.trim(),
          nickname: nickname.trim(),
          password,
        })
        setPassword('')
        setConfirm('')
        navigate('/login', {
          state: { registered: true, username: username.trim() },
        })
      } else {
        signedIn(await authApi.login(username.trim(), password))
      }
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <AuthLayout>
      <div className="auth-form-wrap">
        <div className="auth-switch" aria-label="账号入口">
          <Link className={!register ? 'active' : ''} to="/login">
            登录
          </Link>
          <Link className={register ? 'active' : ''} to="/register">
            注册
          </Link>
        </div>
        <h2>{register ? '写下你的第一程' : '欢迎回来'}</h2>
        <p className="form-intro">
          {register
            ? '创建账号，开始保存自己的旅行想法。'
            : '登录后，继续你的旅行计划。'}
        </p>
        <form onSubmit={submit} aria-busy={busy}>
          <fieldset disabled={busy}>
            <div className="field">
              <label htmlFor="username">用户名</label>
              <input
                id="username"
                name="username"
                autoComplete="username"
                required
                minLength={3}
                maxLength={64}
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="你的用户名"
                autoCapitalize="none"
                spellCheck={false}
                aria-describedby={register ? 'username-hint' : undefined}
              />
              {register && (
                <p id="username-hint" className="field-hint">
                  3–64 位字母、数字或 _ - .，不区分大小写
                </p>
              )}
            </div>
            {register && (
              <div className="field">
                <label htmlFor="nickname">昵称</label>
                <input
                  id="nickname"
                  name="nickname"
                  autoComplete="nickname"
                  required
                  maxLength={64}
                  value={nickname}
                  onChange={(event) => setNickname(event.target.value)}
                  placeholder="希望我们怎么称呼你"
                />
              </div>
            )}
            <PasswordField
              id="password"
              label="密码"
              value={password}
              onChange={setPassword}
              autoComplete={register ? 'new-password' : 'current-password'}
              hint={
                register ? '至少 10 位；建议使用密码管理器生成密码' : undefined
              }
            />
            {register && (
              <PasswordField
                id="confirm-password"
                label="确认密码"
                value={confirm}
                onChange={setConfirm}
                autoComplete="new-password"
              />
            )}
            {error && (
              <p className="message error" role="alert">
                {error}
              </p>
            )}
            <button className="button primary full-width" type="submit">
              {busy
                ? register
                  ? '正在创建账号…'
                  : '正在登录…'
                : register
                  ? '创建账号'
                  : '登录，继续计划'}
            </button>
          </fieldset>
        </form>
        {!register && (
          <button
            type="button"
            className="text-button recovery"
            onClick={() =>
              setNotice('内测期间，请联系管理员进行身份核验和账号恢复。')
            }
          >
            忘记密码？
          </button>
        )}
        {feedback && (
          <p className="message" role="status">
            {feedback}
          </p>
        )}
        <p className="form-bottom">
          {register ? '已有账号？' : '还没有账号？'}{' '}
          <Link to={register ? '/login' : '/register'}>
            {register ? '去登录' : '创建一个'}
          </Link>
        </p>
      </div>
    </AuthLayout>
  )
}
