import { useMemo, useState, type FormEvent } from "react"
import { BriefcaseBusinessIcon, MoreHorizontalIcon, PencilIcon, PlusIcon, SearchIcon, Trash2Icon, UploadIcon } from "lucide-react"
import { PageHeader } from "@/components/PageHeader"
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
import { Card, CardAction, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { toast } from "@/components/ui/toast"
import { api, errorMessage } from "@/lib/api"
import { useCurrentUser } from "@/lib/auth"
import { formatDate } from "@/lib/format"
import type { Vacancy } from "@/lib/types"
import { useQuery } from "@/lib/use-query"

function VacancyFormDialog({
  vacancy,
  open,
  onOpenChange,
  onSaved,
}: {
  vacancy: Vacancy | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [busy, setBusy] = useState(false)
  const [lastOpen, setLastOpen] = useState(false)

  // Preenche o formulário toda vez que o diálogo abre.
  if (open !== lastOpen) {
    setLastOpen(open)
    if (open) {
      setName(vacancy?.name ?? "")
      setDescription(vacancy?.description ?? "")
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      if (vacancy) await api.vacancies.update(vacancy.id, { name, description })
      else await api.vacancies.create(name, description)
      toast.add({ title: vacancy ? "Vaga atualizada" : "Vaga publicada", type: "success" })
      onOpenChange(false)
      onSaved()
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{vacancy ? "Editar vaga" : "Nova vaga"}</DialogTitle>
            <DialogDescription>A descrição é usada pela IA para avaliar os currículos.</DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="vacancy-name">Nome</FieldLabel>
              <Input id="vacancy-name" value={name} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field>
              <FieldLabel htmlFor="vacancy-description">Descrição</FieldLabel>
              <Textarea
                id="vacancy-description"
                rows={6}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
              <FieldDescription>Inclua responsabilidades, requisitos obrigatórios e diferenciais.</FieldDescription>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="submit" disabled={busy}>
              {busy && <Spinner data-icon="inline-start" />}
              {vacancy ? "Salvar" : "Publicar vaga"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function ApplyDialog({
  vacancy,
  onOpenChange,
  onApplied,
}: {
  vacancy: Vacancy | null
  onOpenChange: (open: boolean) => void
  onApplied: () => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!vacancy || !file) return
    setBusy(true)
    try {
      await api.resumes.create(vacancy.id, file)
      toast.add({
        title: "Currículo enviado",
        description: "A IA está analisando. O resultado chega por e-mail e em Minhas candidaturas.",
        type: "success",
      })
      setFile(null)
      onOpenChange(false)
      onApplied()
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={vacancy !== null} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Candidatar-se</DialogTitle>
            <DialogDescription>{vacancy?.name}</DialogDescription>
          </DialogHeader>
          <Field>
            <FieldLabel htmlFor="resume-file">Currículo (PDF)</FieldLabel>
            <Input
              id="resume-file"
              type="file"
              accept="application/pdf,.pdf"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            <FieldDescription>Você pode se candidatar uma vez por vaga.</FieldDescription>
          </Field>
          <DialogFooter>
            <Button type="submit" disabled={busy || !file}>
              {busy ? <Spinner data-icon="inline-start" /> : <UploadIcon data-icon="inline-start" />}
              Enviar currículo
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function Vacancies() {
  const user = useCurrentUser()
  const isAdmin = user.role === "admin"
  const [search, setSearch] = useState("")
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Vacancy | null>(null)
  const [applying, setApplying] = useState<Vacancy | null>(null)
  const [deleting, setDeleting] = useState<Vacancy | null>(null)

  const { data, reload } = useQuery(async () => {
    const [vacancies, resumes] = await Promise.all([api.vacancies.list(), api.resumes.listMine()])
    return { vacancies, resumes }
  })

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (data?.vacancies ?? []).filter(
      (v) => !term || v.name.toLowerCase().includes(term) || v.description.toLowerCase().includes(term),
    )
  }, [data, search])

  const myResume = (vacancyId: number) => data?.resumes.find((r) => r.vancancie_id === vacancyId)

  function openCreate() {
    setEditing(null)
    setFormOpen(true)
  }

  function openEdit(vacancy: Vacancy) {
    setEditing(vacancy)
    setFormOpen(true)
  }

  async function confirmDelete() {
    if (!deleting) return
    try {
      await api.vacancies.remove(deleting.id)
      toast.add({ title: "Vaga excluída", type: "success" })
      setDeleting(null)
      reload()
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    }
  }

  return (
    <>
      <PageHeader
        title="Vagas"
        description={isAdmin ? "Publique e gerencie as vagas abertas." : "Encontre uma vaga e envie seu currículo."}
        actions={
          isAdmin && (
            <Button onClick={openCreate}>
              <PlusIcon data-icon="inline-start" />
              Nova vaga
            </Button>
          )
        }
      />

      <InputGroup className="max-w-sm">
        <InputGroupInput placeholder="Buscar vagas" value={search} onChange={(e) => setSearch(e.target.value)} />
        <InputGroupAddon>
          <SearchIcon />
        </InputGroupAddon>
      </InputGroup>

      {!data ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-48" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <Empty className="border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <BriefcaseBusinessIcon />
            </EmptyMedia>
            <EmptyTitle>Nenhuma vaga encontrada</EmptyTitle>
            <EmptyDescription>
              {search ? "Tente outro termo de busca." : "Ainda não há vagas publicadas."}
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {filtered.map((vacancy) => {
            const resume = myResume(vacancy.id)
            return (
              <Card key={vacancy.id}>
                <CardHeader>
                  <CardTitle>{vacancy.name}</CardTitle>
                  <CardDescription>Publicada em {formatDate(vacancy.created_at)}</CardDescription>
                  {isAdmin && (
                    <CardAction>
                      <DropdownMenu>
                        <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" />}>
                          <MoreHorizontalIcon />
                          <span className="sr-only">Ações da vaga</span>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuGroup>
                            <DropdownMenuItem onClick={() => openEdit(vacancy)}>
                              <PencilIcon />
                              Editar
                            </DropdownMenuItem>
                            <DropdownMenuItem variant="destructive" onClick={() => setDeleting(vacancy)}>
                              <Trash2Icon />
                              Excluir
                            </DropdownMenuItem>
                          </DropdownMenuGroup>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </CardAction>
                  )}
                </CardHeader>
                <CardContent className="flex-1">
                  <p className="line-clamp-4 text-sm text-muted-foreground">{vacancy.description}</p>
                </CardContent>
                <CardFooter>
                  {resume ? (
                    <StatusBadge status={resume.status} />
                  ) : (
                    <Button variant="outline" className="w-full" onClick={() => setApplying(vacancy)}>
                      <UploadIcon data-icon="inline-start" />
                      Candidatar-se
                    </Button>
                  )}
                </CardFooter>
              </Card>
            )
          })}
        </div>
      )}

      <VacancyFormDialog vacancy={editing} open={formOpen} onOpenChange={setFormOpen} onSaved={reload} />
      <ApplyDialog vacancy={applying} onOpenChange={(open) => !open && setApplying(null)} onApplied={reload} />

      <AlertDialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Excluir {deleting?.name}?</AlertDialogTitle>
            <AlertDialogDescription>
              As candidaturas dessa vaga também serão apagadas. Essa ação não pode ser desfeita.
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
