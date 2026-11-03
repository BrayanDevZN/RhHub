import { useState } from "react"
import { DownloadIcon, EyeIcon, FileTextIcon, MoreHorizontalIcon, Trash2Icon } from "lucide-react"
import { StageProgress } from "@/components/StageProgress"
import { StatusBadge } from "@/components/StatusBadge"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { toast } from "@/components/ui/toast"
import { api, errorMessage } from "@/lib/api"
import { formatDate } from "@/lib/format"
import type { Resume, User, Vacancy } from "@/lib/types"

async function downloadFile(resume: Resume) {
  try {
    await api.resumes.download(resume)
  } catch (error) {
    toast.add({ title: "Não foi possível baixar", description: errorMessage(error), type: "error" })
  }
}

export function ResumeTable({
  resumes,
  vacancies,
  users,
  emptyDescription,
  onChanged,
}: {
  resumes: Resume[] | null
  vacancies: Vacancy[]
  users?: User[]
  emptyDescription: string
  onChanged: () => void
}) {
  const [viewing, setViewing] = useState<Resume | null>(null)
  const [deleting, setDeleting] = useState<Resume | null>(null)

  const vacancyName = (id: number) => vacancies.find((v) => v.id === id)?.name ?? "Vaga removida"
  const stagesOf = (id: number) => vacancies.find((v) => v.id === id)?.stages ?? []
  const userName = (id: number) => users?.find((u) => u.id === id)?.name ?? "Usuário removido"

  async function changeStage(resume: Resume, stage: string) {
    try {
      await api.resumes.setStage(resume.id, stage)
      toast.add({ title: `Candidato movido para ${stage}`, description: "Ele foi avisado por e-mail.", type: "success" })
      onChanged()
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    }
  }

  async function confirmDelete() {
    if (!deleting) return
    try {
      await api.resumes.remove(deleting.id)
      toast.add({ title: "Candidatura excluída", type: "success" })
      setDeleting(null)
      onChanged()
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    }
  }

  if (!resumes) return <Skeleton className="h-48 w-full" />

  if (resumes.length === 0) {
    return (
      <Empty className="border">
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <FileTextIcon />
          </EmptyMedia>
          <EmptyTitle>Nenhuma candidatura</EmptyTitle>
          <EmptyDescription>{emptyDescription}</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  const sorted = [...resumes].sort((a, b) => b.created_at.localeCompare(a.created_at))

  return (
    <>
      <Card className="py-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Vaga</TableHead>
              {users && <TableHead>Candidato</TableHead>}
              <TableHead className="hidden sm:table-cell">Arquivo</TableHead>
              <TableHead className="hidden md:table-cell">Enviado em</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Etapa</TableHead>
              <TableHead className="w-10">
                <span className="sr-only">Ações</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.map((resume) => (
              <TableRow key={resume.id}>
                <TableCell className="font-medium">{vacancyName(resume.vacancy_id)}</TableCell>
                {users && <TableCell>{userName(resume.user_id)}</TableCell>}
                <TableCell className="hidden max-w-48 truncate text-muted-foreground sm:table-cell">
                  {resume.file_name}
                </TableCell>
                <TableCell className="hidden text-muted-foreground md:table-cell">
                  {formatDate(resume.created_at)}
                </TableCell>
                <TableCell>
                  <StatusBadge status={resume.status} />
                </TableCell>
                <TableCell>
                  {users && resume.status === "aproved" ? (
                    <Select items={stagesOf(resume.vacancy_id).map((s) => ({ value: s, label: s }))} value={resume.stage} onValueChange={(value) => value && changeStage(resume, value as string)}>
                      <SelectTrigger size="sm" aria-label="Etapa do candidato">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {stagesOf(resume.vacancy_id).map((stage) => (
                            <SelectItem key={stage} value={stage}>
                              {stage}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  ) : (
                    <span className="text-sm text-muted-foreground">{resume.status === "recuse" ? "—" : resume.stage}</span>
                  )}
                </TableCell>
                <TableCell>
                  <DropdownMenu>
                    <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" />}>
                      <MoreHorizontalIcon />
                      <span className="sr-only">Ações</span>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuGroup>
                        <DropdownMenuItem disabled={resume.status === "pending"} onClick={() => setViewing(resume)}>
                          <EyeIcon />
                          Ver resultado
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => downloadFile(resume)}>
                          <DownloadIcon />
                          Baixar arquivo
                        </DropdownMenuItem>
                      </DropdownMenuGroup>
                      <DropdownMenuSeparator />
                      <DropdownMenuGroup>
                        <DropdownMenuItem variant="destructive" onClick={() => setDeleting(resume)}>
                          <Trash2Icon />
                          Excluir
                        </DropdownMenuItem>
                      </DropdownMenuGroup>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      <Dialog open={viewing !== null} onOpenChange={(open) => !open && setViewing(null)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{viewing && vacancyName(viewing.vacancy_id)}</DialogTitle>
            <DialogDescription>Resultado da análise feita pela IA.</DialogDescription>
          </DialogHeader>
          {viewing && (
            <div className="flex flex-col gap-3">
              <StatusBadge status={viewing.status} />
              <p className="text-sm leading-relaxed">{viewing.reason || "Sem detalhes da análise."}</p>
              {stagesOf(viewing.vacancy_id).length > 0 && (
                <StageProgress stages={stagesOf(viewing.vacancy_id)} resume={viewing} />
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Excluir candidatura?</AlertDialogTitle>
            <AlertDialogDescription>
              {deleting && `A candidatura para ${vacancyName(deleting.vacancy_id)} será apagada.`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancelar</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={confirmDelete}>
              Excluir
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
