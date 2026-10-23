import { useMemo, useState } from "react"
import { PageHeader } from "@/components/PageHeader"
import { ResumeTable } from "@/components/ResumeTable"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { api } from "@/lib/api"
import { STATUS_LABEL, type ResumeStatus } from "@/lib/types"
import { useQuery } from "@/lib/use-query"

type Filter = "all" | ResumeStatus

export default function AdminResumes() {
  const [filter, setFilter] = useState<Filter>("all")
  const { data, reload } = useQuery(
    async () => {
      const [resumes, vacancies, users] = await Promise.all([
        api.resumes.listAll(),
        api.vacancies.list(),
        api.admin.listUsers(),
      ])
      return { resumes, vacancies, users }
    },
    { pollMs: 2000, shouldPoll: (d) => d.resumes.some((r) => r.status === "pending") },
  )

  const resumes = useMemo(
    () => (data ? data.resumes.filter((r) => filter === "all" || r.status === filter) : null),
    [data, filter],
  )

  return (
    <>
      <PageHeader title="Todas as candidaturas" description="Currículos recebidos em todas as vagas." />
      <ToggleGroup
        variant="outline"
        value={[filter]}
        onValueChange={(value: string[]) => value[0] && setFilter(value[0] as Filter)}
      >
        <ToggleGroupItem value="all">Todas</ToggleGroupItem>
        <ToggleGroupItem value="pending">{STATUS_LABEL.pending}</ToggleGroupItem>
        <ToggleGroupItem value="aproved">{STATUS_LABEL.aproved}</ToggleGroupItem>
        <ToggleGroupItem value="recuse">{STATUS_LABEL.recuse}</ToggleGroupItem>
      </ToggleGroup>
      <ResumeTable
        resumes={resumes}
        vacancies={data?.vacancies ?? []}
        users={data?.users ?? []}
        emptyDescription="Nenhum currículo com esse filtro."
        onChanged={reload}
      />
    </>
  )
}
