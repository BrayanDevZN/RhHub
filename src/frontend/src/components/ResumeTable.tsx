import { useState } from "react"
import { DownloadIcon, EyeIcon, FileTextIcon, MoreHorizontalIcon, Trash2Icon } from "lucide-react"
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
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { toast } from "@/components/ui/toast"
import { api, errorMessage } from "@/lib/api"
import { formatDate } from "@/lib/format"
import type { Resume, User, Vacancy } from "@/lib/types"

function downloadPdf(resume: Resume) {
  if (!resume.pdf) {
    toast.add({ title: "Arquivo indisponível", description: "PDFs grandes não ficam salvos na demonstração." })
    return
  }
  const bytes = Uint8Array.from(atob(resume.pdf), (c) => c.charCodeAt(0))
  const url = URL.createObjectURL(new Blob([bytes], { type: "application/pdf" }))
  const link = document.createElement("a")
  link.href = url
  link.download = resume.file_name
  link.click()
  URL.revokeObjectURL(url)
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
  const userName = (id: number) => users?.find((u) => u.id === id)?.name ?? "Usuário removido"

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
              <TableHead className="w-10">
                <span className="sr-only">Ações</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.map((resume) => (
              <TableRow key={resume.id}>
                <TableCell className="font-medium">{vacancyName(resume.vancancie_id)}</TableCell>
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
                        <DropdownMenuItem onClick={() => downloadPdf(resume)}>
                          <DownloadIcon />
                          Baixar PDF
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
            <DialogTitle>{viewing && vacancyName(viewing.vancancie_id)}</DialogTitle>
            <DialogDescription>Resultado da análise feita pela IA.</DialogDescription>
          </DialogHeader>
          {viewing && (
            <div className="flex flex-col gap-3">
              <StatusBadge status={viewing.status} />
              <p className="text-sm leading-relaxed">{viewing.reason}</p>
            </div>
          )}
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Excluir candidatura?</AlertDialogTitle>
            <AlertDialogDescription>
              {deleting && `A candidatura para ${vacancyName(deleting.vancancie_id)} será apagada.`}
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
