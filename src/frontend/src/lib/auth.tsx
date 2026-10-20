import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react"

export type User = {
  name: string
  email: string
  provider: "local" | "google"
}

type AuthContextValue = {
  user: User | null
  login: (email: string) => void
  loginWithGoogle: () => void
  register: (name: string, email: string) => void
  logout: () => void
}

const STORAGE_KEY = "rhhub:user"

const AuthContext = createContext<AuthContextValue | null>(null)

function readStoredUser(): User | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

function nameFromEmail(email: string) {
  const local = email.split("@")[0]?.trim()
  if (!local) return "Candidato"
  return local.charAt(0).toUpperCase() + local.slice(1)
}

// Autenticação simulada: qualquer credencial é aceita até o back end existir.
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(readStoredUser)

  const persist = useCallback((next: User | null) => {
    setUser(next)
    try {
      if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
      else localStorage.removeItem(STORAGE_KEY)
    } catch {
      // armazenamento indisponível: mantém só em memória
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      login: (email) =>
        persist({ name: nameFromEmail(email), email: email || "candidato@gmail.com", provider: "local" }),
      loginWithGoogle: () =>
        persist({ name: "Usuário Google", email: "usuario@gmail.com", provider: "google" }),
      register: (name, email) =>
        persist({ name: name || nameFromEmail(email), email: email || "candidato@gmail.com", provider: "local" }),
      logout: () => persist(null),
    }),
    [user, persist],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error("useAuth deve ser usado dentro de AuthProvider")
  return ctx
}
