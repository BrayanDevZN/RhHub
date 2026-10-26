import { Link } from "react-router-dom"
import { BriefcaseBusinessIcon } from "lucide-react"
import { PageHeader } from "@/components/PageHeader"
import { ResumeTable } from "@/components/ResumeTable"
import { Button } from "@/components/ui/button"
import { api } from "@/lib/api"
import { useQuery } from "@/lib/use-query"

export default function MyResumes() {
  const { data, reload } = useQuery(
    async () => {
      const [resumes, vacancies] = await Promise.all([api.resumes.listMine(), api.vacancies.list()])
      return { resumes, vacancies }
    },
    { pollMs: 2000, shouldPoll: (d) => d.resumes.some((r) => r.status === "pending") },
  )

  return (
    <>
      <PageHeader
        title="Minhas candidaturas"
        description="Acompanhe o status da análise de cada currículo enviado."
        actions={
          <Button variant="outline" render={<Link to="/vagas" />}>
            <BriefcaseBusinessIcon data-icon="inline-start" />
            Ver vagas
          </Button>
        }
      />
      <ResumeTable
        resumes={data?.resumes ?? null}
        vacancies={data?.vacancies ?? []}
        emptyDescription="Você ainda não enviou currículos. Escolha uma vaga para começar."
        onChanged={reload}
      />
    </>
  )
}
