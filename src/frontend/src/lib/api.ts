// API simulada. Cada função corresponde a uma rota do back end e devolve o mesmo
// formato; quando o back end Java existir, basta trocar o corpo por fetch(..., { credentials: "include" }).
// Por enquanto qualquer credencial e qualquer código são aceitos.

import type { Gender, Resume, ResumeStatus, Role, SignupData, User, Vacancy } from "@/lib/types"

type Db = {
  users: User[]
  vacancies: Vacancy[]
  resumes: Resume[]
  session: string | null // public_id do usuário logado (no back end: cookie X-user_token)
  seq: number
}

const DB_KEY = "rhhub:db"
const DEMO_EMAIL = "demo@rhhub.local"
const MAX_STORED_PDF = 1_500_000

let memoryDb: Db | null = null

function now() {
  return new Date().toISOString()
}

function seed(): Db {
  const admin: User = {
    id: 1,
    public_id: crypto.randomUUID(),
    name: "Admin RhHub",
    email: "admin@gmail.com",
    cpf: "00000000000",
    age: 30,
    gender: "other",
    permission: false,
    role: "admin",
    provider: "local",
    created_at: now(),
  }
  const vacancies: Vacancy[] = [
    {
      id: 2,
      name: "Desenvolvedor Java Júnior",
      description:
        "Desenvolvimento de APIs REST com Spring Boot, JPA e PostgreSQL. Desejável: Docker, Kafka e testes com JUnit.",
      created_by: 1,
      created_at: now(),
    },
    {
      id: 3,
      name: "Analista de Dados",
      description: "SQL avançado, Python e construção de dashboards. Experiência com modelagem de dados e ETL.",
      created_by: 1,
      created_at: now(),
    },
    {
      id: 4,
      name: "Designer UX/UI",
      description: "Pesquisa com usuários, prototipação no Figma e design systems. Portfólio obrigatório.",
      created_by: 1,
      created_at: now(),
    },
  ]
  return { users: [admin], vacancies, resumes: [], session: null, seq: 5 }
}

function load(): Db {
  if (memoryDb) return memoryDb
  try {
    const raw = localStorage.getItem(DB_KEY)
    memoryDb = raw ? (JSON.parse(raw) as Db) : seed()
  } catch {
    memoryDb = seed()
  }
  return memoryDb
}

function save(db: Db) {
  memoryDb = db
  try {
    localStorage.setItem(DB_KEY, JSON.stringify(db))
  } catch {
    // sem espaço ou armazenamento bloqueado: mantém só em memória
  }
}

function nextId(db: Db) {
  db.seq += 1
  return db.seq
}

function delay<T>(value: T, ms = 350): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), ms))
}

export class ApiError extends Error {}

function fail(message: string): never {
  throw new ApiError(message)
}

function code() {
  return String(Math.floor(100000 + Math.random() * 900000))
}

function nameFromEmail(email: string) {
  const local = email.split("@")[0]?.trim()
  if (!local) return "Candidato"
  return local.charAt(0).toUpperCase() + local.slice(1)
}

function currentUser(db: Db) {
  const user = db.users.find((u) => u.public_id === db.session)
  if (!user) fail("Sessão expirada. Entre novamente.")
  return user
}

function requireAdmin(db: Db) {
  const user = currentUser(db)
  if (user.role !== "admin") fail("Sem permissão.")
  return user
}

// Na simulação, e-mails com "admin" entram como administrador.
function findOrCreate(db: Db, email: string, extra: Partial<User> = {}) {
  const normalized = (email || "candidato@gmail.com").trim().toLowerCase()
  let user = db.users.find((u) => u.email === normalized)
  if (!user) {
    user = {
      id: nextId(db),
      public_id: crypto.randomUUID(),
      name: nameFromEmail(normalized),
      email: normalized,
      cpf: null,
      age: null,
      gender: null,
      permission: false,
      role: normalized.includes("admin") ? "admin" : "user",
      provider: "local",
      created_at: now(),
      ...extra,
    }
    db.users.push(user)
  }
  return user
}

// Permite navegar pelo prototipo antes da integracao com o back end.
function enterDemoSession(db: Db): User {
  const demo = findOrCreate(db, DEMO_EMAIL, {
    name: "Visitante RhHub",
    role: "admin",
    provider: "local",
  })
  demo.role = "admin"
  db.session = demo.public_id
  save(db)
  return demo
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result).split(",")[1] ?? "")
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

const ANALYSIS_MS = 6000

// Simula o consumer do topic resume.analysis: depois de alguns segundos a IA decide.
// Roda a cada leitura, então funciona mesmo se a página for recarregada no meio.
function settleAnalyses(db: Db) {
  let changed = false
  for (const resume of db.resumes) {
    if (resume.status !== "pending") continue
    if (Date.now() - new Date(resume.created_at).getTime() < ANALYSIS_MS) continue
    const vacancy = db.vacancies.find((v) => v.id === resume.vancancie_id)
    const approved = resume.file_name.length % 3 !== 0
    const status: ResumeStatus = approved ? "aproved" : "recuse"
    resume.status = status
    resume.reason = approved
      ? `Candidatura aprovada para ${vacancy?.name ?? "a vaga"}. Seu currículo mostra experiência alinhada aos requisitos principais. Nossa equipe entrará em contato para a próxima etapa.`
      : `Candidatura não aprovada para ${vacancy?.name ?? "a vaga"}. O currículo não evidenciou os requisitos obrigatórios da vaga. Agradecemos o interesse e incentivamos novas candidaturas.`
    changed = true
  }
  if (changed) save(db)
}

export const api = {
  auth: {
    // GET /users (quem está logado)
    async me(): Promise<User | null> {
      const db = load()
      const active = db.users.find((u) => u.public_id === db.session)
      if (active) return delay(active, 150)

      // /login e /cadastro continuam acessiveis para testar os formularios.
      const publicAuthPage = /^\/(?:login|cadastro)\/?$/.test(window.location.pathname)
      return delay(publicAuthPage ? null : enterDemoSession(db), 150)
    },

    async enterDemo(): Promise<User> {
      return delay(enterDemoSession(load()), 150)
    },

    // POST /sender: envia código de criação de conta
    async sendSignupCode(_name: string, _email: string): Promise<{ code: string }> {
      return delay({ code: code() })
    },

    // POST /users
    async signup(data: Partial<SignupData>, _code: string): Promise<User> {
      const db = load()
      const user = findOrCreate(db, data.email ?? "", {
        name: data.name || nameFromEmail(data.email ?? ""),
        cpf: data.cpf || null,
        age: data.age ?? null,
        gender: data.gender ?? null,
      })
      db.session = user.public_id
      save(db)
      return delay(user)
    },

    // GET /auth: devolve se precisa de 2FA
    async login(email: string, _password: string): Promise<{ twoFactor: boolean; user: User | null }> {
      const db = load()
      const user = findOrCreate(db, email)
      if (user.permission) {
        save(db)
        return delay({ twoFactor: true, user: null })
      }
      db.session = user.public_id
      save(db)
      return delay({ twoFactor: false, user })
    },

    // POST /sender/2fa
    async sendTwoFactorCode(_email: string): Promise<{ code: string }> {
      return delay({ code: code() })
    },

    // GET /auth com code
    async verifyTwoFactor(email: string, _code: string): Promise<User> {
      const db = load()
      const user = findOrCreate(db, email)
      db.session = user.public_id
      save(db)
      return delay(user)
    },

    // GET /oauth2/authorization/google (simulado: não abre o Google)
    async loginWithGoogle(): Promise<User> {
      const db = load()
      const user = findOrCreate(db, "usuario.google@gmail.com", { name: "Usuário Google", provider: "google" })
      db.session = user.public_id
      save(db)
      return delay(user, 600)
    },

    // DELETE /auth
    async logout(): Promise<void> {
      const db = load()
      db.session = null
      save(db)
      return delay(undefined, 150)
    },

    // PATCH /sender: código para trocar senha
    async sendPasswordResetCode(_email: string): Promise<{ code: string }> {
      return delay({ code: code() })
    },

    // PATCH /auth com sender=true
    async resetPassword(_email: string, _code: string, _newPassword: string): Promise<void> {
      return delay(undefined)
    },

    // PATCH /auth com a senha atual
    async changePassword(_current: string, _next: string): Promise<void> {
      currentUser(load())
      return delay(undefined)
    },
  },

  users: {
    // PATCH /users: set em permission, age ou gender
    async update(set: "permission" | "age" | "gender", value: boolean | number | Gender): Promise<User> {
      const db = load()
      const user = currentUser(db)
      if (set === "permission") user.permission = value as boolean
      if (set === "age") user.age = value as number
      if (set === "gender") user.gender = value as Gender
      save(db)
      return delay(user)
    },

    // DELETE /users
    async deleteMe(_password: string): Promise<void> {
      const db = load()
      const user = currentUser(db)
      db.users = db.users.filter((u) => u.id !== user.id)
      db.resumes = db.resumes.filter((r) => r.user_id !== user.id)
      db.session = null
      save(db)
      return delay(undefined)
    },
  },

  admin: {
    // GET /admin com is_all
    async listUsers(): Promise<User[]> {
      const db = load()
      requireAdmin(db)
      return delay(db.users)
    },

    // PATCH /admin
    async setRole(publicId: string, role: Role): Promise<void> {
      const db = load()
      requireAdmin(db)
      const user = db.users.find((u) => u.public_id === publicId) ?? fail("Usuário não encontrado.")
      user.role = role
      save(db)
      return delay(undefined)
    },

    // DELETE /admin
    async deleteUser(publicId: string): Promise<void> {
      const db = load()
      const admin = requireAdmin(db)
      if (admin.public_id === publicId) fail("Use o seu perfil para excluir a própria conta.")
      const user = db.users.find((u) => u.public_id === publicId) ?? fail("Usuário não encontrado.")
      db.users = db.users.filter((u) => u.id !== user.id)
      db.resumes = db.resumes.filter((r) => r.user_id !== user.id)
      save(db)
      return delay(undefined)
    },
  },

  vacancies: {
    // GET /vancancies com search=all
    async list(): Promise<Vacancy[]> {
      const db = load()
      currentUser(db)
      return delay([...db.vacancies].sort((a, b) => b.created_at.localeCompare(a.created_at)))
    },

    // POST /vancancies
    async create(name: string, description: string): Promise<Vacancy> {
      const db = load()
      const admin = requireAdmin(db)
      if (db.vacancies.some((v) => v.name.toLowerCase() === name.trim().toLowerCase())) {
        fail(`Já existe uma vaga chamada ${name}.`)
      }
      const vacancy: Vacancy = {
        id: nextId(db),
        name: name.trim() || "Nova vaga",
        description: description.trim(),
        created_by: admin.id,
        created_at: now(),
      }
      db.vacancies.push(vacancy)
      save(db)
      return delay(vacancy)
    },

    // PATCH /vancancies
    async update(id: number, data: { name: string; description: string }): Promise<Vacancy> {
      const db = load()
      requireAdmin(db)
      const vacancy = db.vacancies.find((v) => v.id === id) ?? fail("Vaga não encontrada.")
      vacancy.name = data.name.trim() || vacancy.name
      vacancy.description = data.description.trim()
      save(db)
      return delay(vacancy)
    },

    // DELETE /vancancies (as candidaturas da vaga vão junto, como o cascade do model)
    async remove(id: number): Promise<void> {
      const db = load()
      requireAdmin(db)
      db.vacancies = db.vacancies.filter((v) => v.id !== id)
      db.resumes = db.resumes.filter((r) => r.vancancie_id !== id)
      save(db)
      return delay(undefined)
    },
  },

  resumes: {
    // GET /resumes do usuário logado
    async listMine(): Promise<Resume[]> {
      const db = load()
      const user = currentUser(db)
      settleAnalyses(db)
      return delay(db.resumes.filter((r) => r.user_id === user.id), 150)
    },

    // GET /resumes com get_all (admin)
    async listAll(): Promise<Resume[]> {
      const db = load()
      requireAdmin(db)
      settleAnalyses(db)
      return delay(db.resumes, 150)
    },

    // POST /resumes (multipart) e publica em resume.analysis
    async create(vacancyId: number, file: File): Promise<Resume> {
      const db = load()
      const user = currentUser(db)
      if (!db.vacancies.some((v) => v.id === vacancyId)) fail("Vaga não encontrada.")
      if (db.resumes.some((r) => r.user_id === user.id && r.vancancie_id === vacancyId)) {
        fail("Você já se candidatou a essa vaga.")
      }
      const pdf = file.size <= MAX_STORED_PDF ? await fileToBase64(file) : null
      const resume: Resume = {
        id: nextId(db),
        user_id: user.id,
        vancancie_id: vacancyId,
        file_name: file.name,
        pdf,
        status: "pending",
        reason: "null",
        created_at: now(),
      }
      db.resumes.push(resume)
      save(db)
      return delay(resume, 600)
    },

    // DELETE /resumes
    async remove(id: number): Promise<void> {
      const db = load()
      const user = currentUser(db)
      const resume = db.resumes.find((r) => r.id === id) ?? fail("Candidatura não encontrada.")
      if (resume.user_id !== user.id && user.role !== "admin") fail("Sem permissão.")
      db.resumes = db.resumes.filter((r) => r.id !== id)
      save(db)
      return delay(undefined)
    },
  },
}

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Algo deu errado. Tente de novo."
}
