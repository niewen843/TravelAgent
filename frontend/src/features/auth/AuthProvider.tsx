import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { AuthContext } from './auth-context'
import { ApiError } from '../../shared/api/client'
import { authApi } from './api'
import type { Account } from './types'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [account, setAccount] = useState<Account | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const generation = useRef(0)
  const channel = useRef<BroadcastChannel | null>(null)
  const currentAccount = useRef<Account | null>(null)
  useEffect(() => {
    currentAccount.current = account
  }, [account])
  const clear = useCallback(() => {
    generation.current += 1
    queryClient.clear()
    setAccount(null)
    setError('')
  }, [queryClient])
  const refresh = useCallback(async (background = false) => {
    const current = ++generation.current
    if (!background) setLoading(true)
    setError('')
    try {
      const user = await authApi.me()
      if (current === generation.current) setAccount(user)
    } catch (cause) {
      if (current === generation.current) {
        setAccount(null)
        if (!(cause instanceof ApiError && cause.status === 401))
          setError((cause as Error).message)
      }
    } finally {
      if (!background && current === generation.current) setLoading(false)
    }
  }, [])
  useEffect(() => {
    let active = true
    // StrictMode discards its first effect before this microtask, avoiding an orphaned startup request.
    void Promise.resolve().then(() => {
      if (active) return refresh()
    })
    channel.current =
      typeof BroadcastChannel === 'undefined'
        ? null
        : new BroadcastChannel('travel-auth')
    if (channel.current)
      channel.current.onmessage = () => {
        clear()
        void refresh()
      }
    const expired = () => {
      clear()
      setLoading(false)
      channel.current?.postMessage('changed')
    }
    const focus = () => {
      // An anonymous form contains no private account data. Re-check in the background so
      // returning from a password manager does not unmount the form and erase the user's input.
      if (currentAccount.current) {
        clear()
        void refresh()
      } else {
        void refresh(true)
      }
    }
    window.addEventListener('auth:expired', expired)
    window.addEventListener('focus', focus)
    return () => {
      active = false
      generation.current += 1
      channel.current?.close()
      window.removeEventListener('auth:expired', expired)
      window.removeEventListener('focus', focus)
    }
  }, [clear, refresh])
  const signedIn = (user: Account) => {
    setNotice('')
    queryClient.clear()
    generation.current += 1
    setAccount(user)
    setLoading(false)
    setError('')
    channel.current?.postMessage('changed')
  }
  const signOut = async () => {
    try {
      await authApi.logout()
    } catch (cause) {
      if (!(cause instanceof ApiError && cause.status === 401)) throw cause
    }
    clear()
    setNotice('')
    setLoading(false)
    channel.current?.postMessage('changed')
  }
  const passwordChanged = () => {
    clear()
    setNotice('密码已修改，请使用新密码登录。')
    setLoading(false)
    channel.current?.postMessage('changed')
  }
  return (
    <AuthContext.Provider
      value={{
        account,
        loading,
        error,
        notice,
        refresh,
        signedIn,
        signOut,
        passwordChanged,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}
