import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { AccountLayout } from '../../shared/ui/AccountLayout'
import { PasswordField } from '../../shared/ui/PasswordField'
import { authApi } from '../auth/api'
import { useAuth } from '../auth/auth-context'

export function PasswordPage() {
  const { account, passwordChanged } = useAuth()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setError('')
    if (next !== confirm) {
      setError('两次输入的新密码不一致')
      return
    }
    if (next.length < 10 || new TextEncoder().encode(next).length > 72) {
      setError('新密码至少 10 位，且不能超过 72 个 UTF-8 字节')
      return
    }
    setBusy(true)
    try {
      await authApi.password(current, next)
      navigate('/login', { replace: true, state: { passwordChanged: true } })
      passwordChanged()
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <AccountLayout>
      <section className="password-page">
        <h1>{account?.mustChangePassword ? '先设置一个新密码' : '修改密码'}</h1>
        <p>
          {account?.mustChangePassword
            ? '请先修改初始密码或临时密码，完成后重新登录即可正常使用。'
            : '修改后，所有设备都需要重新登录。'}
        </p>
        <form onSubmit={submit}>
          <fieldset disabled={busy}>
            <PasswordField
              id="current-password"
              label="当前密码"
              value={current}
              onChange={setCurrent}
            />
            <PasswordField
              id="new-password"
              label="新密码"
              value={next}
              onChange={setNext}
              autoComplete="new-password"
              hint="至少 10 位；不能与当前密码相同"
            />
            <PasswordField
              id="confirm-password"
              label="确认新密码"
              value={confirm}
              onChange={setConfirm}
              autoComplete="new-password"
            />
            {error && (
              <p className="message error" role="alert">
                {error}
              </p>
            )}
            <button className="button primary" type="submit">
              {busy ? '正在修改…' : '保存新密码'}
            </button>
          </fieldset>
        </form>
      </section>
    </AccountLayout>
  )
}
