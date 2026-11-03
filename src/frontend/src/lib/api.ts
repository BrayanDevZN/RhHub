// Cliente da API real. Todas as chamadas vão para /api no mesmo domínio do front (o nginx repassa para o back end,
// por isso os cookies HttpOnly de sessão funcionam). Os tokens nunca passam pelo JavaScript.

import type { Gender, Resume, ResumeStatus, Role, SignupData, User, Vacancy } from "@/lib/types"

const BASE = "/api"

export class ApiError extends Error {
  status: number

  constructor(message: string, status = 0) {
    super(message)
    this.status = status
  }
}

/** Disparado quando a sessão acabou de vez (o refresh também falhou): o AuthProvider volta para o login. */
export const UNAUTHORIZED_EVENT = "rhhub:unauthorized"

const FALLBACK: Record<number, string> = {
  400: "Confira os dados e tente de novo.",
  401: "Sessão expirada. Entre novamente.",
  403: "Você não tem permissão para isso.",
  404: "Não encontrado.",
  409: "Esse registro já existe.",
  422: "Não foi possível concluir.",
  429: "Muitas tentativas. Aguarde um instante.",
  502: "O servidor está indisponível no momento.",
  503: "O servidor está indisponível no momento.",
}

async function toError(response: Response): Promise<ApiError> {
  let message = ""
  try {
    const body = await response.json()
    if (body && typeof body.message === "string") message = body.message
  } catch {
    // corpo que não é JSON (por exemplo, página de erro do proxy)
  }
  return new ApiError(message || FALLBACK[response.status] || "Algo deu errado. Tente de novo.", response.status)
}

let refreshing: Promise<boolean> | null = null

/** Renova o token de acesso uma vez, mesmo que várias chamadas falhem juntas. */
function refreshSession(): Promise<boolean> {
  refreshing ??= fetch(`${BASE}/auth/refresh`, { method: "POST", credentials: "include" })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

async function request(path: string, init: RequestInit = {}, retry = true): Promise<Response> {
  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, { credentials: "include", ...init })
  } catch {
    throw new ApiError("Sem conexão com o servidor. Verifique sua internet.")
  }
  if (response.status === 401 && retry && !path.startsWith("/auth/")) {
    if (await refreshSession()) return request(path, init, false)
    if (path !== "/users/me") window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }
  if (!response.ok) throw await toError(response)
  return response
}

async function call<T = void>(path: string, method = "GET", body?: unknown): Promise<T> {
  const init: RequestInit = { method }
  if (body !== undefined) {
    init.headers = { "Content-Type": "application/json" }
    init.body = JSON.stringify(body)
  }
  const response = await request(path, init)
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

function upload<T = void>(path: string, method: string, form: FormData): Promise<T> {
  return request(path, { method, body: form }).then(async (response) => {
    const text = await response.text()
    return (text ? JSON.parse(text) : undefined) as T
  })
}

type CodeSent = { code?: string } // o código só vem na resposta em ambiente de teste; em produção chega por e-mail

export const api = {
  auth: {
    // GET /users/me (quem está logado); null se não houver sessão
    async me(): Promise<User | null> {
      try {
        return await call<User>("/users/me")
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },

    // GET /auth/providers: quais logins externos estão ligados
    async providers(): Promise<{ google: boolean }> {
      try {
        return await call<{ google: boolean }>("/auth/providers")
      } catch {
        return { google: false }
      }
    },

    // POST /sender/signup-code
    sendSignupCode(name: string, email: string): Promise<CodeSent> {
      return call<CodeSent>("/sender/signup-code", "POST", { name, email })
    },

    // POST /users (já abre a sessão)
    signup(data: Partial<SignupData>, code: string): Promise<User> {
      return call<User>("/users", "POST", { ...data, code })
    },

    // POST /auth/login: sem 2FA abre a sessão; com 2FA devolve só o cookie temporário
    async login(email: string, password: string): Promise<{ twoFactor: boolean; user: User | null }> {
      const result = await call<{ auth: boolean | string }>("/auth/login", "POST", { email, password })
      if (result.auth === "required") return { twoFactor: true, user: null }
      return { twoFactor: false, user: await call<User>("/users/me") }
    },

    // POST /auth/2fa/send (usa o cookie temporário do login)
    sendTwoFactorCode(): Promise<CodeSent> {
      return call<CodeSent>("/auth/2fa/send", "POST")
    },

    // POST /auth/2fa/verify
    verifyTwoFactor(code: string): Promise<User> {
      return call<User>("/auth/2fa/verify", "POST", { code })
    },

    // Login e cadastro com Google: o botão sempre aparece. Se o back end estiver com o Google desligado ou fora do ar,
    // avisa em vez de mandar o navegador para uma página de erro. Ligado, sai do app e volta logado pelo back end.
    async startGoogleLogin(): Promise<void> {
      const { google } = await api.auth.providers()
      if (!google) {
        throw new ApiError("O login com Google não está disponível no momento. Use seu e-mail e senha.")
      }
      window.location.assign("/oauth2/authorization/google")
    },

    // POST /auth/logout
    async logout(): Promise<void> {
      try {
        await call("/auth/logout", "POST")
      } catch {
        // sem sessão ou sem rede: o cookie some de qualquer forma quando expira
      }
    },

    // POST /auth/password/reset-code
    sendPasswordResetCode(email: string): Promise<CodeSent> {
      return call<CodeSent>("/auth/password/reset-code", "POST", { email })
    },

    // POST /auth/password/reset
    resetPassword(email: string, code: string, newPassword: string): Promise<void> {
      return call("/auth/password/reset", "POST", { email, code, newPassword })
    },

    // PATCH /auth/password (a troca encerra as sessões: é preciso entrar de novo)
    changePassword(current: string, next: string): Promise<void> {
      return call("/auth/password", "PATCH", { password: current, newPassword: next })
    },
  },

  users: {
    // PATCH /users/me: set em age, gender, two_factor ou cpf (o CPF só pode ser preenchido uma vez)
    update(set: "two_factor" | "age" | "gender" | "cpf", value: boolean | number | string | Gender): Promise<User> {
      return call<User>("/users/me", "PATCH", { set, value })
    },

    // DELETE /users/me
    deleteMe(password: string): Promise<void> {
      return call("/users/me", "DELETE", { password })
    },

    // PUT /users/me/photo (JPEG, PNG ou WebP, até 2 MB)
    async uploadPhoto(file: File): Promise<void> {
      const form = new FormData()
      form.append("file", file)
      await upload("/users/me/photo", "PUT", form)
    },

    // DELETE /users/me/photo
    deletePhoto(): Promise<void> {
      return call("/users/me/photo", "DELETE")
    },

    // GET /users/me/photo. O parâmetro v força o navegador a buscar de novo depois de uma troca.
    photoUrl(version: number | string = 0): string {
      return `${BASE}/users/me/photo?v=${version}`
    },
  },

  admin: {
    // GET /admin/users
    listUsers(): Promise<User[]> {
      return call<User[]>("/admin/users")
    },

    // PATCH /admin/users/{publicId}/role (rebaixar é só do admin raiz)
    async setRole(publicId: string, role: Role): Promise<void> {
      await call(`/admin/users/${publicId}/role`, "PATCH", { role })
    },

    // DELETE /admin/users/{publicId} (só do admin raiz)
    deleteUser(publicId: string): Promise<void> {
      return call(`/admin/users/${publicId}`, "DELETE")
    },

    // GET /admin/users/{publicId}/photo
    photoUrl(publicId: string): string {
      return `${BASE}/admin/users/${publicId}/photo`
    },
  },

  vacancies: {
    // GET /vacancies
    list(): Promise<Vacancy[]> {
      return call<Vacancy[]>("/vacancies")
    },

    // POST /vacancies
    create(name: string, description: string): Promise<Vacancy> {
      return call<Vacancy>("/vacancies", "POST", { name, description })
    },

    // PATCH /vacancies/{id}
    update(id: number, data: { name: string; description: string }): Promise<Vacancy> {
      return call<Vacancy>(`/vacancies/${id}`, "PATCH", data)
    },

    // DELETE /vacancies/{id} (as candidaturas da vaga vão junto)
    remove(id: number): Promise<void> {
      return call(`/vacancies/${id}`, "DELETE")
    },
  },

  resumes: {
    // GET /resumes/me
    listMine(): Promise<Resume[]> {
      return call<Resume[]>("/resumes/me")
    },

    // GET /resumes (admin)
    listAll(): Promise<Resume[]> {
      return call<Resume[]>("/resumes")
    },

    // POST /resumes (multipart: vacancyId e file, PDF ou DOCX). A análise da IA começa em seguida.
    create(vacancyId: number, file: File): Promise<Resume> {
      const form = new FormData()
      form.append("vacancyId", String(vacancyId))
      form.append("file", file)
      return upload<Resume>("/resumes", "POST", form)
    },

    // GET /resumes/{id}/file: baixa o arquivo original
    async download(resume: Resume): Promise<void> {
      const response = await request(`/resumes/${resume.id}/file`)
      const url = URL.createObjectURL(await response.blob())
      const link = document.createElement("a")
      link.href = url
      link.download = resume.file_name
      link.click()
      URL.revokeObjectURL(url)
    },

    // DELETE /resumes/{id}
    remove(id: number): Promise<void> {
      return call(`/resumes/${id}`, "DELETE")
    },
  },
}

export type { ResumeStatus }

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Algo deu errado. Tente de novo."
}
