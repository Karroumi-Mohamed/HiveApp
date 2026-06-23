import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'
import {
  clearAdminTokens,
  getAdminAccessToken,
  setAdminTokens,
} from '../../../lib/admin-session'
import { getAdminMe, loginAdmin, type AdminMe } from './api'
import {
  AdminSessionContext,
  type AdminSessionContextValue,
  type SessionStatus,
} from './AdminSessionContext'

export function AdminSessionProvider({ children }: PropsWithChildren) {
  const [status, setStatus] = useState<SessionStatus>('checking')
  const [user, setUser] = useState<AdminMe | null>(null)

  const loadUser = useCallback(async () => {
    if (!getAdminAccessToken()) {
      setUser(null)
      setStatus('unauthenticated')
      return
    }

    try {
      const me = await getAdminMe()
      setUser(me)
      setStatus('authenticated')
    } catch {
      clearAdminTokens()
      setUser(null)
      setStatus('unauthenticated')
    }
  }, [])

  useEffect(() => {
    void loadUser()
  }, [loadUser])

  const signIn = useCallback(async (email: string, password: string) => {
    const response = await loginAdmin({ email, password })
    setAdminTokens({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
    })
    const me = await getAdminMe()
    setUser(me)
    setStatus('authenticated')
  }, [])

  const signOut = useCallback(() => {
    clearAdminTokens()
    setUser(null)
    setStatus('unauthenticated')
  }, [])

  const hasPermission = useCallback(
    (permission: string) => Boolean(user?.isSuperAdmin || user?.permissions.includes(permission)),
    [user],
  )

  const value = useMemo<AdminSessionContextValue>(
    () => ({
      status,
      user,
      signIn,
      signOut,
      refreshSession: loadUser,
      hasPermission,
    }),
    [hasPermission, loadUser, signIn, signOut, status, user],
  )

  return <AdminSessionContext.Provider value={value}>{children}</AdminSessionContext.Provider>
}
