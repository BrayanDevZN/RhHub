// Formatos que a API devolve (campos em snake_case). Ver a seção 4.5 da DOCUMENTACAO.md.

export type Role = "admin" | "user"
export type Gender = "male" | "female" | "other"
export type ResumeStatus = "aproved" | "recuse" | "pending"
export type Provider = "local" | "google"

export type User = {
  id: number
  public_id: string
  name: string
  email: string
  cpf: string | null
  age: number | null
  gender: Gender | null
  two_factor: boolean // verificação em duas etapas por e-mail
  role: Role
  provider: Provider
  has_photo?: boolean // só vem em GET /users/me
  created_at: string
}

export type Vacancy = {
  id: number
  name: string
  description: string
  created_by?: number // só admins recebem
  created_at: string
}

export type Resume = {
  id: number
  user_id: number
  vacancy_id: number
  file_name: string
  content_type: string // application/pdf ou DOCX
  status: ResumeStatus
  reason: string | null
  created_at: string
}

export type SignupData = {
  name: string
  email: string
  cpf: string
  age: number
  gender: Gender
  password: string
}

export const STATUS_LABEL: Record<ResumeStatus, string> = {
  aproved: "Aprovado",
  recuse: "Recusado",
  pending: "Em análise",
}

export const GENDER_LABEL: Record<Gender, string> = {
  male: "Masculino",
  female: "Feminino",
  other: "Outro",
}
