import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react"
import { api, UNAUTHORIZED_EVENT } from "@/lib/api"
import type { User } from "@/lib/types"

type AuthContextValue = {
  user: User | null
  loading: boolean
  /** Muda a cada troca ou remoção da foto, para o navegador buscar a imagem de novo. */
  photoVersion: number
  bumpPhoto: () => void
  setUser: (user: User | null) => void
  refresh: () => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [photoVersion, setPhotoVersion] = useState(0)
  const bumpPhoto = useCallback(() => setPhotoVersion((v) => v + 1), [])

  const refresh = useCallback(async () => {
    setUser(await api.auth.me())
  }, [])

  useEffect(() => {
    refresh()
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
  }, [refresh])

  // A API avisa quando a sessão acabou de vez (token e refresh vencidos): volta para o login.
  useEffect(() => {
    const onUnauthorized = () => setUser(null)
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      photoVersion,
      bumpPhoto,
      setUser,
      refresh,
      logout: async () => {
        await api.auth.logout()
        setUser(null)
      },
    }),
    [user, loading, photoVersion, bumpPhoto, refresh],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error("useAuth deve ser usado dentro de AuthProvider")
  return ctx
}

export function useCurrentUser() {
  const { user } = useAuth()
  if (!user) throw new Error("useCurrentUser exige usuário logado")
  return user
}
