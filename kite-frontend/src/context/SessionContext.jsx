import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { getSessionStatus } from '../api/client'

const SessionContext = createContext(null)

const LOADING_STATUS = {
  loading: true,
  loggedIn: false,
  userId: null,
  kiteConnected: false,
  kiteExpiresAt: null,
}

export function SessionProvider({ children }) {
  const [status, setStatus] = useState(LOADING_STATUS)

  const refresh = useCallback(() => {
    setStatus((s) => ({ ...s, loading: true }))
    return getSessionStatus().then((s) => setStatus({ ...s, loading: false }))
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  return <SessionContext.Provider value={{ ...status, refresh }}>{children}</SessionContext.Provider>
}

export function useSession() {
  const context = useContext(SessionContext)
  if (!context) {
    throw new Error('useSession must be used within a SessionProvider')
  }
  return context
}
