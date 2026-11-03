import { useState } from 'react'

export function PasswordField({
  id,
  label,
  value,
  onChange,
  autoComplete = 'current-password',
  hint,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete?: string
  hint?: string
}) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="password-input">
        <input
          id={id}
          name={id}
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          autoComplete={autoComplete}
          required
          maxLength={72}
          aria-describedby={hint ? `${id}-hint` : undefined}
        />
        <button
          type="button"
          className="password-toggle"
          aria-label={visible ? `隐藏${label}` : `显示${label}`}
          aria-pressed={visible}
          onClick={() => setVisible(!visible)}
        >
          {visible ? '隐藏' : '显示'}
        </button>
      </div>
      {hint && (
        <p className="field-hint" id={`${id}-hint`}>
          {hint}
        </p>
      )}
    </div>
  )
}
