import { Link } from "react-router-dom"
import {
  ArrowRightIcon, ArrowUpRightIcon, BriefcaseBusinessIcon, CheckCircle2Icon,
  ChevronRightIcon, Clock3Icon, FileTextIcon, LightbulbIcon,
  SearchIcon, SparklesIcon, TrendingUpIcon,
} from "lucide-react"
import { StatusBadge } from "@/components/StatusBadge"
import { Skeleton } from "@/components/ui/skeleton"
import { api } from "@/lib/api"
import { useCurrentUser } from "@/lib/auth"
import { formatDate } from "@/lib/format"
import { useQuery } from "@/lib/use-query"
import type { ResumeStatus } from "@/lib/types"

function StatCard({ label, count, icon: Icon, color, caption }: {
  label: string
  count: number | undefined
  icon: typeof FileTextIcon
  color: "violet" | "blue" | "amber" | "green"
  caption: string
}) {
  return (
    <article className="rh-stat-card">
      <div className="flex items-start justify-between">
        <div className={`rh-stat-icon rh-stat-${color}`}><Icon className="size-5" strokeWidth={2} /></div>
        <span className="rh-stat-trend"><TrendingUpIcon className="size-3.5" /> Agora</span>
      </div>
      <p className="rh-stat-value">{count === undefined ? <Skeleton className="h-10 w-14" /> : count.toLocaleString("pt-BR")}</p>
      <h3 className="rh-stat-label">{label}</h3>
      <p className="rh-stat-caption">{caption}</p>
    </article>
  )
}

const statusLegend: { status: ResumeStatus; label: string; color: string }[] = [
  { status: "aproved", label: "Aprovadas", color: "var(--rh-success)" },
  { status: "pending", label: "Em análise", color: "var(--rh-warning)" },
  { status: "recuse", label: "Não aprovadas", color: "var(--rh-danger)" },
]

export default function Dashboard() {
  const user = useCurrentUser()
  const isAdmin = user.role === "admin"
  const { data } = useQuery(
    async () => {
      const [vacancies, resumes] = await Promise.all([
        api.vacancies.list(),
        isAdmin ? api.resumes.listAll() : api.resumes.listMine(),
      ])
      return { vacancies, resumes }
    },
    { pollMs: 2000, shouldPoll: (d) => d.resumes.some((r) => r.status === "pending") },
  )

  const resumes = data?.resumes ?? []
  const recent = [...resumes].sort((a, b) => b.created_at.localeCompare(a.created_at)).slice(0, 4)
  const countStatus = (status: ResumeStatus) => resumes.filter((item) => item.status === status).length
  const vacancyName = (id: number) => data?.vacancies.find((item) => item.id === id)?.name ?? "Vaga indisponível"
  const firstName = user.name.split(" ")[0] || "você"
  const totals = statusLegend.map((entry) => ({ ...entry, count: countStatus(entry.status) }))

  return (
    <div className="rh-dashboard">
      <div className="rh-welcome-row">
        <div>
          <div className="rh-eyebrow"><span className="rh-eyebrow-dot" /> VISÃO GERAL DO WORKSPACE</div>
          <h1 className="rh-page-title">Olá, {firstName} <span className="rh-greeting-wave">✳</span></h1>
          <p className="rh-page-subtitle"> {isAdmin ? "Confira o panorama do seu processo seletivo." : "Aqui está um resumo da sua jornada profissional."}</p>
        </div>
        <Link to="/vagas" className="rh-primary-action"><SearchIcon className="size-[17px]" /> Explorar vagas <ArrowUpRightIcon className="size-4" /></Link>
      </div>

      <section className="rh-hero" aria-label="Destaque">
        <div className="rh-hero-copy">
          <span className="rh-hero-chip"><SparklesIcon className="size-3.5" /> SUA JORNADA COMEÇA AQUI</span>
          <h2>{isAdmin ? "Encontre os melhores talentos." : "Sua próxima grande oportunidade está aqui."}</h2>
          <p>{isAdmin ? "Publique oportunidades, analise currículos e acompanhe cada etapa em um só lugar." : "Descubra vagas, envie seu currículo e acompanhe suas candidaturas com clareza e agilidade."}</p>
          <Link to="/vagas" className="rh-hero-btn">{isAdmin ? "Gerenciar oportunidades" : "Encontrar minha vaga"} <ArrowRightIcon className="size-4" /></Link>
        </div>
        <div className="rh-hero-art" aria-hidden="true">
          <div className="rh-hero-ring rh-ring-outer" /><div className="rh-hero-ring rh-ring-inner" />
          <div className="rh-orb rh-orb-a" /><div className="rh-orb rh-orb-b" />
          <div className="rh-floating-panel rh-panel-a"><span className="rh-floating-icon"><CheckCircle2Icon className="size-4" /></span><span><strong>Processo inteligente</strong><small>Triagem com IA</small></span></div>
          <div className="rh-floating-panel rh-panel-b"><BriefcaseBusinessIcon className="size-5" /><span>Novas oportunidades</span><ArrowUpRightIcon className="size-3.5" /></div>
          <div className="rh-hero-center"><BriefcaseBusinessIcon className="size-16" strokeWidth={1.35} /></div>
        </div>
      </section>

      <section aria-label="Indicadores da plataforma">
        <div className="rh-section-heading"><div><h2>Seu panorama</h2><p>Principais números, atualizados conforme as candidaturas.</p></div><span className="rh-section-overline">RESUMO ATUAL</span></div>
        <div className="rh-stat-grid">
          <StatCard label="Vagas disponíveis" count={data?.vacancies.length} icon={BriefcaseBusinessIcon} color="violet" caption="Oportunidades publicadas" />
          <StatCard label={isAdmin ? "Candidaturas recebidas" : "Minhas candidaturas"} count={data?.resumes.length} icon={FileTextIcon} color="blue" caption="Inscrições registradas" />
          <StatCard label="Em análise" count={data ? countStatus("pending") : undefined} icon={Clock3Icon} color="amber" caption="Aguardando avaliação" />
          <StatCard label="Aprovadas" count={data ? countStatus("aproved") : undefined} icon={CheckCircle2Icon} color="green" caption="Resultados positivos" />
        </div>
      </section>

      <div className="rh-overview-grid">
        <section className="rh-content-panel">
          <div className="rh-panel-heading">
            <div><span className="rh-panel-kicker">ACOMPANHAMENTO</span><h2>Candidaturas recentes</h2><p>Últimas movimentações da sua jornada.</p></div>
            <Link to={isAdmin ? "/admin/candidaturas" : "/candidaturas"} className="rh-text-link">Ver todas <ArrowUpRightIcon className="size-4" /></Link>
          </div>
          {!data ? <div className="p-6"><Skeleton className="h-40 w-full" /></div> : recent.length === 0 ? (
            <div className="rh-empty-state">
              <span className="rh-empty-icon"><FileTextIcon className="size-7" /></span>
              <h3>Nenhuma candidatura por enquanto</h3>
              <p>{isAdmin ? "As candidaturas recebidas aparecerão aqui." : "Que tal encontrar uma vaga que combine com você?"}</p>
              <Link to="/vagas" className="rh-outline-btn">Explorar oportunidades <ArrowRightIcon className="size-4" /></Link>
            </div>
          ) : (
            <div className="rh-activity-list">
              {recent.map((resume) => (
                <div key={resume.id} className="rh-activity-item">
                  <span className="rh-activity-icon"><FileTextIcon className="size-5" /></span>
                  <div className="min-w-0 flex-1"><h3 className="truncate">{vacancyName(resume.vancancie_id)}</h3><p>{formatDate(resume.created_at)} · {resume.file_name}</p></div>
                  <StatusBadge status={resume.status} />
                  <ChevronRightIcon className="size-4 text-[#a0aac0]" />
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="rh-content-panel rh-status-panel">
          <div className="rh-panel-heading">
            <div><span className="rh-panel-kicker">ANÁLISE DE PERFIL</span><h2>Status das candidaturas</h2><p>Visão geral dos resultados.</p></div>
            <span className="rh-panel-head-icon"><TrendingUpIcon className="size-5" /></span>
          </div>
          <div className="rh-progress-summary"><div className="rh-progress-total">{data ? resumes.length : "—"}<span>candidaturas</span></div><span className="rh-progress-summary-icon"><SparklesIcon className="size-6" /></span></div>
          <div className="rh-progress-stacked">{totals.map(({status,color,count}) => <span key={status} style={{ width: resumes.length ? `${(count / resumes.length) * 100}%` : "0%", background: color }} />)}{resumes.length === 0 && <span style={{ width: "100%", background: "var(--rh-track)" }} />}</div>
          <div className="rh-status-list">{totals.map(({status,label,color,count}) => <div key={status}><span><i style={{ background: color }} />{label}</span><strong>{data ? count : "—"}</strong></div>)}</div>
          <Link className="rh-status-more" to={isAdmin ? "/admin/candidaturas" : "/candidaturas"}>Acompanhar detalhes <ArrowRightIcon className="size-4" /></Link>
        </section>
      </div>

      <section className="rh-bottom-grid">
        <div className="rh-opportunities">
          <div className="rh-section-heading"><div><h2>Oportunidades em destaque</h2><p>Explore as vagas disponíveis na plataforma.</p></div><Link to="/vagas" className="rh-text-link">Todas as vagas <ArrowUpRightIcon className="size-4" /></Link></div>
          <div className="rh-opportunity-list">{!data ? <Skeleton className="h-36 w-full" /> : (data.vacancies.slice(0, 3).map((vacancy) => (
            <Link to="/vagas" key={vacancy.id} className="rh-opportunity-row"><span className="rh-opportunity-logo"><BriefcaseBusinessIcon className="size-[21px]" /></span><span className="min-w-0 flex-1"><strong className="truncate">{vacancy.name}</strong><small>RhHub · Oportunidade aberta</small></span><span className="rh-opportunity-pill">Aberta</span><ArrowRightIcon className="size-4 text-[#8d98ac]" /></Link>
          )))}</div>
        </div>
        <div className="rh-tip-card"><span className="rh-tip-icon"><LightbulbIcon className="size-6" /></span><span className="rh-panel-kicker">DICA PARA VOCÊ</span><h3>Seu currículo faz a diferença.</h3><p>Para uma análise mais precisa, mantenha suas experiências e habilidades atualizadas no PDF enviado.</p><Link to="/vagas">Descobrir oportunidades <ArrowUpRightIcon className="size-4" /></Link></div>
      </section>
    </div>
  )
}
