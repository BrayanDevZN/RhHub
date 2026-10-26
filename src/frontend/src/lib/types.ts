// Formatos espelhando os models do back end (users, vancancies, resumes).

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
  permission: boolean // 2FA por e-mail ativado
  role: Role
  provider: Provider
  created_at: string
}

export type Vacancy = {
  id: number
  name: string
  description: string
  created_by: number
  created_at: string
}

export type Resume = {
  id: number
  user_id: number
  vancancie_id: number
  file_name: string
  pdf: string | null // base64, como o GET /resumes devolve
  status: ResumeStatus
  reason: string
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
