export function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString("pt-BR", { day: "2-digit", month: "short", year: "numeric" })
}

export function initials(name: string) {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("")
}

/** A vaga com data de expiração já vencida não recebe mais candidaturas. */
export function isExpired(vacancy: { expires_at?: string | null }) {
  return !!vacancy.expires_at && new Date(vacancy.expires_at).getTime() <= Date.now()
}

/** ISO vindo da API -> valor do <input type="date"> (yyyy-mm-dd, no fuso do navegador). */
export function toDateInput(iso?: string | null) {
  if (!iso) return ""
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** yyyy-mm-dd -> fim desse dia no fuso do navegador, em ISO (a vaga vale o dia inteiro). */
export function endOfDayIso(date: string) {
  return new Date(`${date}T23:59:59`).toISOString()
}
