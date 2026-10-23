import { lazy, Suspense } from "react"
import { Link } from "react-router-dom"
import { ArrowRightIcon, BriefcaseBusinessIcon, CircleCheckIcon, FileTextIcon, HourglassIcon } from "lucide-react"
import { PageHeader } from "@/components/PageHeader"
import { StatusBadge } from "@/components/StatusBadge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { api } from "@/lib/api"
import { useCurrentUser } from "@/lib/auth"
import { formatDate } from "@/lib/format"
import { useQuery } from "@/lib/use-query"

const Scene3D = lazy(() => import("@/components/Scene3D"))

function Stat({ label, value, icon: Icon }: { label: string; value: number | undefined; icon: typeof FileTextIcon }) {
  return (
    <Card>
      <CardHeader>
        <CardDescription>{label}</CardDescription>
        <CardTitle className="text-3xl tabular-nums">
          {value === undefined ? <Skeleton className="h-9 w-12" /> : value}
        </CardTitle>
        <CardAction>
          <Icon className="text-muted-foreground" />
        </CardAction>
      </CardHeader>
    </Card>
  )
}

export default function Dashboard() {
  const user = useCurrentUser()
  const { data } = useQuery(
    async () => {
      const [vacancies, resumes] = await Promise.all([api.vacancies.list(), api.resumes.listMine()])
      return { vacancies, resumes }
    },
    { pollMs: 2000, shouldPoll: (d) => d.resumes.some((r) => r.status === "pending") },
  )

  const resumes = data?.resumes
  const vacancyName = (id: number) => data?.vacancies.find((v) => v.id === id)?.name ?? "Vaga removida"
  const recent = resumes ? [...resumes].sort((a, b) => b.created_at.localeCompare(a.created_at)).slice(0, 5) : []

  return (
    <>
      <Card className="relative overflow-hidden">
        <Suspense fallback={null}>
          <Scene3D className="pointer-events-none absolute inset-y-0 right-0 hidden w-1/2 opacity-80 md:block" />
        </Suspense>
        <CardHeader className="relative">
          <CardDescription>Bem-vindo de volta</CardDescription>
          <CardTitle className="text-2xl">Olá, {user.name}!</CardTitle>
        </CardHeader>
        <CardContent className="relative flex flex-col gap-4">
          <p className="max-w-md text-sm text-muted-foreground">
            Envie seu currículo para uma vaga e a nossa IA faz a triagem. O resultado chega por e-mail e aparece aqui.
          </p>
          <div>
            <Button render={<Link to="/vagas" />}>
              Ver vagas abertas
              <ArrowRightIcon data-icon="inline-end" />
            </Button>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Vagas abertas" value={data?.vacancies.length} icon={BriefcaseBusinessIcon} />
        <Stat label="Minhas candidaturas" value={resumes?.length} icon={FileTextIcon} />
        <Stat label="Em análise" value={resumes?.filter((r) => r.status === "pending").length} icon={HourglassIcon} />
        <Stat label="Aprovadas" value={resumes?.filter((r) => r.status === "aproved").length} icon={CircleCheckIcon} />
      </div>

      <div className="flex flex-col gap-4">
        <PageHeader title="Candidaturas recentes" />
        {!resumes ? (
          <Skeleton className="h-32 w-full" />
        ) : recent.length === 0 ? (
          <Empty className="border">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <FileTextIcon />
              </EmptyMedia>
              <EmptyTitle>Nenhuma candidatura ainda</EmptyTitle>
              <EmptyDescription>Escolha uma vaga e envie seu currículo em PDF.</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <Card>
            <CardContent className="flex flex-col divide-y">
              {recent.map((resume) => (
                <div key={resume.id} className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
                  <div className="flex min-w-0 flex-col">
                    <span className="truncate font-medium">{vacancyName(resume.vancancie_id)}</span>
                    <span className="text-xs text-muted-foreground">{formatDate(resume.created_at)}</span>
                  </div>
                  <StatusBadge status={resume.status} />
                </div>
              ))}
            </CardContent>
          </Card>
        )}
      </div>
    </>
  )
}
