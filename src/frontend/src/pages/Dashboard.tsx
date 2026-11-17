import { lazy, Suspense, useEffect, useRef, useState, useSyncExternalStore, type CSSProperties, type ReactNode } from "react"
import { Link } from "react-router-dom"
import {
  ArrowRightIcon, ArrowUpRightIcon, BriefcaseBusinessIcon, CheckCircle2Icon,
  ChevronRightIcon, Clock3Icon, FileTextIcon, LightbulbIcon,
  SearchIcon, SparklesIcon, TrendingUpIcon,
} from "lucide-react"
import { StatusBadge } from "@/components/StatusBadge"
import { Badge } from "@/components/ui/badge"
import { buttonVariants } from "@/components/ui/button"
import {
  Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle,
} from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { api } from "@/lib/api"
import { useCurrentUser } from "@/lib/auth"
import { formatDate } from "@/lib/format"
import { useQuery } from "@/lib/use-query"
import { cn } from "@/lib/utils"
import type { ResumeStatus } from "@/lib/types"

const Scene3D = lazy(() => import("@/components/Scene3D"))

function useMediaQuery(query: string) {
  return useSyncExternalStore(
    (notify) => {
      const mql = window.matchMedia(query)
      mql.addEventListener("change", notify)
      return () => mql.removeEventListener("change", notify)
    },
    () => window.matchMedia(query).matches,
    () => false,
  )
}

/** Faz o número subir até o valor novo (e de um valor para o outro quando a API atualiza). Sem animação se a pessoa pediu menos movimento. */
function useCountUp(target: number | undefined) {
  const [shown, setShown] = useState(0)
  const from = useRef(0)
  useEffect(() => {
    if (target === undefined) return
    const start = from.current
    const duration = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 700
    const t0 = performance.now()
    let frame = 0
    const tick = (now: number) => {
      const progress = duration === 0 ? 1 : Math.min(1, (now - t0) / duration)
      const value = Math.round(start + (target - start) * (1 - Math.pow(1 - progress, 3)))
      from.current = value
      setShown(value)
      if (progress < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target])
  return target === undefined ? undefined : shown
}

function HeroCenter() {
  return (
    <div className="absolute top-1/2 left-[55%] -translate-1/2">
      <div className="rh-bob grid size-[125px] -rotate-8 place-items-center rounded-[32px] border-[9px] border-white/15 bg-linear-to-br from-indigo-400 via-violet-400 to-cyan-400 text-white shadow-[0_25px_80px_rgb(2_6_30/65%),inset_0_3px_20px_rgb(255_255_255/22%)]">
        <BriefcaseBusinessIcon className="size-16" strokeWidth={1.35} />
      </div>
    </div>
  )
}

/** Cena 3D do destaque, carregada sob demanda. No celular e com "reduzir movimento" fica o cartão estático. */
function HeroScene() {
  const wide = useMediaQuery("(min-width: 741px)")
  const calm = useMediaQuery("(prefers-reduced-motion: reduce)")
  if (!wide || calm) return <HeroCenter />
  return (
    <Suspense fallback={<HeroCenter />}>
      <Scene3D className="pointer-events-none absolute inset-0 z-[1]" />
    </Suspense>
  )
}

const statTones = {
  violet: "bg-indigo-400/20 text-violet-300",
  blue: "bg-cyan-400/15 text-cyan-300",
  amber: "bg-pink-400/15 text-pink-300",
  green: "bg-emerald-400/15 text-emerald-300",
}

function StatCard({ label, count, icon: Icon, color, caption, index }: {
  label: string
  count: number | undefined
  icon: typeof FileTextIcon
  color: keyof typeof statTones
  caption: string
  index: number
}) {
  const shown = useCountUp(count)
  return (
    <Card className="rh-rise min-w-0 gap-0 transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:ring-primary/40" style={{ "--d": `${0.15 + index * 0.08}s` } as CSSProperties}>
      <CardContent className="flex flex-col gap-1 p-4 sm:p-5">
        <div className="flex items-start justify-between gap-2">
          <span className={cn("grid size-10 place-items-center rounded-xl", statTones[color])}><Icon className="size-5" /></span>
          <Badge variant="secondary" className="gap-1 text-[10px]"><TrendingUpIcon className="size-3" /> Agora</Badge>
        </div>
        <div className="mt-4 text-3xl leading-tight font-bold tracking-tight tabular-nums sm:text-4xl">
          {shown === undefined ? <Skeleton className="h-10 w-14" /> : shown.toLocaleString("pt-BR")}
        </div>
        <h3 className="mt-1 text-sm font-semibold">{label}</h3>
        <p className="text-xs text-muted-foreground">{caption}</p>
      </CardContent>
    </Card>
  )
}

function SectionHeading({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <div className="mb-4 flex items-end justify-between gap-4">
      <div>
        <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
      </div>
      {action}
    </div>
  )
}

function TextLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="inline-flex items-center gap-1.5 text-sm font-medium whitespace-nowrap text-cyan-300 transition-colors hover:text-violet-300">
      {children}
    </Link>
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
  const listTo = isAdmin ? "/admin/candidaturas" : "/candidaturas"

  return (
    <div className="flex min-w-0 flex-col gap-8">
      <div className="rh-rise flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="mb-3 flex items-center gap-2 text-[11px] font-bold tracking-[0.15em] text-indigo-300">
            <span className="rh-dot block size-2 rounded-full bg-cyan-400 shadow-[0_0_0_4px_rgb(34_211_238/13%)]" /> VISÃO GERAL DO WORKSPACE
          </div>
          <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Olá, {firstName} <span className="align-middle text-2xl text-amber-400">✳</span></h1>
          <p className="mt-2 text-sm text-muted-foreground">{isAdmin ? "Confira o panorama do seu processo seletivo." : "Aqui está um resumo da sua jornada profissional."}</p>
        </div>
        <Link to="/vagas" className={cn(buttonVariants({ size: "lg" }), "h-10 w-full gap-2 bg-linear-to-br from-indigo-400 to-violet-400 px-4 text-white shadow-lg shadow-indigo-500/20 hover:brightness-110 sm:w-auto")}>
          <SearchIcon /> Explorar vagas <ArrowUpRightIcon />
        </Link>
      </div>

      <section aria-label="Destaque" className="rh-rise relative isolate flex min-h-[235px] items-center overflow-hidden rounded-2xl border border-violet-400/40 bg-[radial-gradient(circle_at_75%_26%,rgb(34_211_238/24%)_0%,transparent_34%),radial-gradient(circle_at_53%_90%,rgb(244_114_182/18%)_0%,transparent_35%),linear-gradient(110deg,#212b60_0%,#343a85_55%,#275477_100%)] px-6 py-8 text-white shadow-xl shadow-black/25 sm:min-h-[250px] sm:px-10 lg:px-14" style={{ "--d": ".08s" } as CSSProperties}>
        <div className="relative z-[3] max-w-[530px]">
          <Badge variant="outline" className="h-auto gap-1.5 border-white/15 bg-white/15 px-2.5 py-1.5 text-[10px] font-bold tracking-widest text-white"><SparklesIcon className="size-3.5" /> SUA JORNADA COMEÇA AQUI</Badge>
          <h2 className="mt-4 mb-2.5 max-w-[470px] text-[26px] leading-tight font-bold tracking-tight sm:text-4xl">{isAdmin ? "Encontre os melhores talentos." : "Sua próxima grande oportunidade está aqui."}</h2>
          <p className="max-w-[465px] text-sm leading-relaxed text-indigo-50/90">{isAdmin ? "Publique oportunidades, analise currículos e acompanhe cada etapa em um só lugar." : "Descubra vagas, envie seu currículo e acompanhe suas candidaturas com clareza e agilidade."}</p>
          <Link to="/vagas" className={cn(buttonVariants({ size: "lg" }), "mt-5 h-10 gap-2 bg-white px-4 text-[#293a81] hover:bg-indigo-50")}>
            {isAdmin ? "Gerenciar oportunidades" : "Encontrar minha vaga"} <ArrowRightIcon />
          </Link>
        </div>
        <div aria-hidden="true" className="absolute inset-y-0 right-0 hidden w-2/5 opacity-40 min-[741px]:block min-[951px]:opacity-70 min-[1171px]:opacity-100">
          <div className="absolute top-1/2 left-[55%] -translate-1/2"><div className="rh-pulse aspect-square w-[370px] rounded-full border border-white/20" /></div>
          <div className="absolute top-1/2 left-[55%] -translate-1/2"><div className="rh-pulse aspect-square w-[260px] rounded-full border border-white/20 bg-white/5" style={{ "--d": "-3.5s" } as CSSProperties} /></div>
          <div className="rh-drift absolute top-[12%] right-[20%] size-[120px] rounded-full bg-violet-300 opacity-20 blur-[20px]" />
          <div className="rh-drift absolute right-[5%] bottom-0 size-20 rounded-full bg-violet-300 opacity-25 blur-[20px]" style={{ "--d": "-4s" } as CSSProperties} />
          <div className="rh-bob absolute top-6 -left-1 z-[2] flex items-center gap-2 rounded-xl border border-indigo-400/35 bg-[#202d53] p-3 text-[11px] font-semibold text-indigo-50 shadow-xl shadow-black/35">
            <span className="grid size-8 place-items-center rounded-lg bg-emerald-400/15 text-emerald-400"><CheckCircle2Icon className="size-4" /></span>
            <span><strong className="block">Processo inteligente</strong><small className="mt-0.5 block text-[10px] text-blue-200">Triagem com IA</small></span>
          </div>
          <div className="rh-bob absolute right-2.5 bottom-6 z-[2] flex items-center gap-2 rounded-xl border border-indigo-400/35 bg-[#202d53] p-3 text-[11px] font-semibold text-indigo-50 shadow-xl shadow-black/35" style={{ "--d": "-2.5s" } as CSSProperties}>
            <BriefcaseBusinessIcon className="size-5" /><span>Novas oportunidades</span><ArrowUpRightIcon className="size-3.5" />
          </div>
          <HeroScene />
        </div>
      </section>

      <section aria-label="Indicadores da plataforma">
        <SectionHeading title="Seu panorama" description="Principais números, atualizados conforme as candidaturas." action={<span className="hidden text-[11px] font-bold tracking-widest text-indigo-300 sm:block">RESUMO ATUAL</span>} />
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
          <StatCard index={0} label="Vagas disponíveis" count={data?.vacancies.length} icon={BriefcaseBusinessIcon} color="violet" caption="Oportunidades publicadas" />
          <StatCard index={1} label={isAdmin ? "Candidaturas recebidas" : "Minhas candidaturas"} count={data?.resumes.length} icon={FileTextIcon} color="blue" caption="Inscrições registradas" />
          <StatCard index={2} label="Em análise" count={data ? countStatus("pending") : undefined} icon={Clock3Icon} color="amber" caption="Aguardando avaliação" />
          <StatCard index={3} label="Aprovadas" count={data ? countStatus("aproved") : undefined} icon={CheckCircle2Icon} color="green" caption="Resultados positivos" />
        </div>
      </section>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1.6fr)_minmax(270px,1fr)]">
        <Card className="rh-rise min-w-0 gap-0 py-0" style={{ "--d": ".3s" } as CSSProperties}>
          <CardHeader className="border-b py-5">
            <span className="text-[10px] font-bold tracking-widest text-indigo-300">ACOMPANHAMENTO</span>
            <CardTitle className="text-base">Candidaturas recentes</CardTitle>
            <CardDescription>Últimas movimentações da sua jornada.</CardDescription>
            <CardAction><TextLink to={listTo}>Ver todas <ArrowUpRightIcon className="size-4" /></TextLink></CardAction>
          </CardHeader>
          {!data ? <CardContent className="p-5"><Skeleton className="h-40 w-full" /></CardContent> : recent.length === 0 ? (
            <Empty className="min-h-[234px]">
              <EmptyHeader>
                <EmptyMedia variant="icon"><FileTextIcon /></EmptyMedia>
                <EmptyTitle>Nenhuma candidatura por enquanto</EmptyTitle>
                <EmptyDescription>{isAdmin ? "As candidaturas recebidas aparecerão aqui." : "Que tal encontrar uma vaga que combine com você?"}</EmptyDescription>
              </EmptyHeader>
              <Link to="/vagas" className={buttonVariants({ variant: "outline" })}>Explorar oportunidades <ArrowRightIcon /></Link>
            </Empty>
          ) : (
            <ul className="divide-y px-5">
              {recent.map((resume) => (
                <li key={resume.id} className="rh-rise flex items-center gap-3 py-3.5" style={{ "--d": ".45s" } as CSSProperties}>
                  <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary/15 text-primary"><FileTextIcon className="size-5" /></span>
                  <div className="min-w-0 flex-1">
                    <h3 className="truncate text-sm font-semibold">{vacancyName(resume.vacancy_id)}</h3>
                    <p className="truncate text-xs text-muted-foreground">{formatDate(resume.created_at)} · {resume.file_name}</p>
                  </div>
                  <StatusBadge status={resume.status} />
                  <ChevronRightIcon className="hidden size-4 text-muted-foreground sm:block" />
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card className="rh-rise min-w-0 gap-0 py-0" style={{ "--d": ".3s" } as CSSProperties}>
          <CardHeader className="border-b py-5">
            <span className="text-[10px] font-bold tracking-widest text-indigo-300">ANÁLISE DE PERFIL</span>
            <CardTitle className="text-base">Status das candidaturas</CardTitle>
            <CardDescription>Visão geral dos resultados.</CardDescription>
            <CardAction><span className="grid size-9 place-items-center rounded-lg bg-primary/15 text-primary"><TrendingUpIcon className="size-5" /></span></CardAction>
          </CardHeader>
          <CardContent className="flex flex-1 flex-col gap-5 p-5">
            <div className="flex items-center justify-between">
              <div className="text-4xl leading-none font-bold tracking-tight tabular-nums">{data ? resumes.length : "—"}<span className="ml-2 text-xs font-normal tracking-normal text-muted-foreground">candidaturas</span></div>
              <SparklesIcon className="size-6 text-violet-300" />
            </div>
            <div className="flex h-2.5 overflow-hidden rounded-full bg-(--rh-track)" role="img" aria-label="Distribuição das candidaturas por status">
              {totals.map(({ status, color, count }) => (
                <span key={status} className="rh-grow h-full" style={{ width: resumes.length ? `${(count / resumes.length) * 100}%` : "0%", background: color }} />
              ))}
            </div>
            <ul className="flex flex-col gap-2.5">
              {totals.map(({ status, label, color, count }) => (
                <li key={status} className="flex items-center justify-between text-sm">
                  <span className="flex items-center gap-2 text-muted-foreground"><i className="size-2 rounded-full" style={{ background: color }} />{label}</span>
                  <strong className="font-semibold">{data ? count : "—"}</strong>
                </li>
              ))}
            </ul>
          </CardContent>
          <Link className="flex items-center justify-between border-t px-5 py-4 text-sm font-medium text-cyan-300 transition-colors hover:text-violet-300" to={listTo}>Acompanhar detalhes <ArrowRightIcon className="size-4" /></Link>
        </Card>
      </div>

      <section className="grid gap-4 lg:grid-cols-[minmax(0,1.6fr)_minmax(270px,1fr)]">
        <div className="rh-rise min-w-0" style={{ "--d": ".3s" } as CSSProperties}>
          <SectionHeading title="Oportunidades em destaque" description="Explore as vagas disponíveis na plataforma." action={<TextLink to="/vagas">Todas as vagas <ArrowUpRightIcon className="size-4" /></TextLink>} />
          <Card className="gap-0 py-0">
            {!data ? <CardContent className="p-5"><Skeleton className="h-28 w-full" /></CardContent> : data.vacancies.length === 0 ? (
              <p className="px-5 py-8 text-center text-sm text-muted-foreground">Nenhuma vaga publicada por enquanto.</p>
            ) : (
              <ul className="divide-y">
                {data.vacancies.slice(0, 3).map((vacancy) => (
                  <li key={vacancy.id} className="rh-rise" style={{ "--d": ".45s" } as CSSProperties}>
                    <Link to="/vagas" className="flex items-center gap-3 px-5 py-3.5 transition-colors hover:bg-muted/50">
                      <span className="grid size-10 shrink-0 place-items-center overflow-hidden rounded-xl bg-primary/15 text-primary">
                        {vacancy.has_photo ? <img src={api.vacancies.photoUrl(vacancy.id)} alt="" loading="lazy" className="size-full object-cover" /> : <BriefcaseBusinessIcon className="size-5" />}
                      </span>
                      <span className="min-w-0 flex-1">
                        <strong className="block truncate text-sm">{vacancy.name}</strong>
                        <small className="text-xs text-muted-foreground">RhHub · Oportunidade aberta</small>
                      </span>
                      <Badge variant="secondary" className="bg-emerald-400/15 text-emerald-300">Aberta</Badge>
                      <ArrowRightIcon className="hidden size-4 text-muted-foreground sm:block" />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
        <Card className="rh-rise min-h-60 justify-center border-0 bg-linear-to-br from-indigo-400/15 to-violet-400/10 ring-indigo-400/25" style={{ "--d": ".3s" } as CSSProperties}>
          <CardContent className="flex flex-col gap-2 p-5">
            <span className="mb-2 grid size-10 place-items-center rounded-xl bg-violet-400/25 text-violet-200"><LightbulbIcon className="size-5" /></span>
            <span className="text-[10px] font-bold tracking-widest text-indigo-300">DICA PARA VOCÊ</span>
            <h3 className="max-w-64 text-lg leading-tight font-bold tracking-tight">Seu currículo faz a diferença.</h3>
            <p className="text-sm leading-relaxed text-muted-foreground">Para uma análise mais precisa, mantenha suas experiências e habilidades atualizadas no PDF enviado.</p>
            <TextLink to="/vagas">Descobrir oportunidades <ArrowUpRightIcon className="size-4" /></TextLink>
          </CardContent>
        </Card>
      </section>
    </div>
  )
}
