import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { Account } from '../auth/types'
import { PasswordField } from '../../shared/ui/PasswordField'
import { adminApi } from './api'
import type { PasswordReset } from './api'
import './admin.css'

/** 密码只保存在面板组件内存；关闭、切换用户或退出后销毁，不进入查询缓存/浏览器存储。 */
export function ResetPasswordPanel({
  user,
  onClose,
  onReset,
}: {
  user: Account
  onClose: () => void
  onReset: () => void
}) {
  const panel = useRef<HTMLElement>(null)
  const [password, setPassword] = useState('')
  const [result, setResult] = useState<PasswordReset | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [expired, setExpired] = useState(false)
  useEffect(() => {
    panel.current?.querySelector('input')?.focus()
  }, [])
  useEffect(() => {
    if (!result) return
    panel.current
      ?.querySelector<HTMLInputElement>('#temporary-password')
      ?.focus()
    // 到期即清除屏幕上的临时密码；真正的有效性始终由后端校验。
    const timer = window.setTimeout(
      () => {
        setResult(null)
        setExpired(true)
      },
      Math.max(0, new Date(result.expiresAt).getTime() - Date.now()),
    )
    return () => window.clearTimeout(timer)
  }, [result])
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      const reset = await adminApi.resetPassword(user.id, password)
      setResult(reset)
      setExpired(false)
      onReset()
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setPassword('')
      setBusy(false)
    }
  }
  return (
    <section ref={panel} className="reset-panel" aria-labelledby="reset-title">
      <div className="section-heading">
        <h2 id="reset-title">重置用户密码</h2>
        <button className="text-button" disabled={busy} onClick={onClose}>
          关闭
        </button>
      </div>
      <p>
        目标：{user.nickname}（{user.username}），用户 ID {user.id}
      </p>
      {result ? (
        <div className="reset-result" role="status">
          <p>
            密码已重置，所有设备已退出。用户必须先修改密码才能使用其他功能。
          </p>
          <label htmlFor="temporary-password">临时密码（仅本次展示）</label>
          <input
            id="temporary-password"
            className="temporary-password"
            value={result.temporaryPassword}
            readOnly
            autoComplete="off"
            spellCheck={false}
            onFocus={(event) => event.target.select()}
          />
          <p>
            有效至{' '}
            {new Date(result.expiresAt).toLocaleString('zh-CN', {
              hour12: false,
            })}
            。请核实用户身份后交付；关闭后无法再次查看。
          </p>
          <button className="button secondary" onClick={onClose}>
            已保存，关闭
          </button>
        </div>
      ) : (
        <form onSubmit={submit} className="reset-form">
          <p>
            此操作会撤销该用户所有设备的登录。临时密码需在有效期内完成改密，过期后需要重新重置。
          </p>
          {expired && (
            <p className="message" role="status">
              临时密码已过期，可重新验证身份并重置。
            </p>
          )}
          <fieldset disabled={busy}>
            <PasswordField
              id="admin-password"
              label="管理员当前密码"
              value={password}
              onChange={setPassword}
              hint="输入您自己的当前密码，验证后才会执行重置。"
            />
            {error && (
              <p className="message error" role="alert">
                {error}
              </p>
            )}
            <button className="button primary" type="submit">
              {busy ? '正在验证并重置…' : '验证身份并重置密码'}
            </button>
          </fieldset>
        </form>
      )}
    </section>
  )
}
