import { createContext, useContext } from 'react'
import type { Account } from './types'

export type AuthState = {
  account: Account | null
  loading: boolean
  error: string
  notice: string
  refresh: () => Promise<void>
  signedIn: (account: Account) => void
  signOut: () => Promise<void>
  passwordChanged: () => void
}
export const AuthContext = createContext<AuthState | null>(null)
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth requires AuthProvider')
  return context
}
