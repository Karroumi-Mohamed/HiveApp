import { createContext, useContext } from 'react'
import type { AdminMe } from './api'

export type SessionStatus = 'checking' | 'authenticated' | 'unauthenticated'

export type AdminSessionContextValue = {
  status: SessionStatus
  user: AdminMe | null
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => void
  refreshSession: () => Promise<void>
  hasPermission: (permission: string) => boolean
}

export const AdminSessionContext = createContext<AdminSessionContextValue | undefined>(undefined)

export function useAdminSession() {
  const context = useContext(AdminSessionContext)
  if (!context) {
    throw new Error('useAdminSession must be used inside AdminSessionProvider')
  }
  return context
}
