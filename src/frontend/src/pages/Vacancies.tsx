import { useEffect, useMemo, useState, type FormEvent } from "react"
import {
  BriefcaseBusinessIcon,
  CalendarIcon,
  ImageIcon,
  MoreHorizontalIcon,
  PencilIcon,
  PlusIcon,
  SearchIcon,
  Trash2Icon,
  UploadIcon,
  UsersIcon,
} from "lucide-react"
import { cn } from "cn"
import { PageHeader } from "@/components/PageHeader"
import { StageEditor } from "@/components/StageEditor"
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
import { Badge } from "@/components/ui/badge"
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
import { endOfDayIso, formatDate, isExpired, toDateInput } from "@/lib/format"
import { DEFAULT_STAGES, type Resume, type Vacancy } from "@/lib/types"
import { useQuery } from "@/lib/use-query"

const PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"]
const PHOTO_MAX = 2 * 1024 * 1024

/** Capa da vaga: a foto, ou um fundo com ícone quando ela não tem (ou não carrega). */
function VacancyCover({ vacancy, version, className }: { vacancy: Vacancy; version: number; className?: string }) {
  const [failed, setFailed] = useState(false)
  return (
    <div className={cn("relative overflow-hidden bg-muted", className)}>
      {vacancy.has_photo && !failed ? (
        <img
          src={api.vacancies.photoUrl(vacancy.id, version)}
          alt=""
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
          className="size-full object-cover transition-transform duration-500 group-hover/vacancy:scale-105"
        />
      ) : (
        <div className="grid size-full place-items-center bg-linear-to-br from-indigo-500/30 via-violet-500/20 to-cyan-400/25">
          <BriefcaseBusinessIcon className="size-10 text-foreground/35" />
        </div>
      )}
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-1/3 bg-linear-to-t from-black/35 to-transparent" />
    </div>
  )
}

/** Texto com várias linhas vira lista (tirando "-", "•" ou "*" do começo); com uma linha só, parágrafo. */
function FormattedText({ text }: { text: string }) {
  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
  if (lines.length <= 1) return <p className="text-sm leading-relaxed whitespace-pre-line">{text}</p>
  return (
    <ul className="flex list-disc flex-col gap-1.5 pl-5 text-sm leading-relaxed marker:text-muted-foreground">
      {lines.map((line, i) => (
        <li key={i}>{line.replace(/^[-•*]\s+/, "")}</li>
      ))}
    </ul>
  )
}

/** Prazo da vaga e, para o admin, quantos candidatos ela já tem. */
function VacancyMeta({ vacancy, isAdmin }: { vacancy: Vacancy; isAdmin: boolean }) {
  const expired = isExpired(vacancy)
  if (!vacancy.expires_at && !(isAdmin && vacancy.applicants !== undefined)) return null
  return (
    <div className="flex flex-wrap gap-1.5">
      {vacancy.expires_at && (
        <Badge variant={expired ? "destructive" : "outline"}>
          <CalendarIcon data-icon="inline-start" />
          {expired ? "Encerrada" : `Até ${formatDate(vacancy.expires_at)}`}
        </Badge>
      )}
      {isAdmin && vacancy.applicants !== undefined && (
        <Badge variant="secondary">
          <UsersIcon data-icon="inline-start" />
          {vacancy.applicants === 1 ? "1 candidato" : `${vacancy.applicants} candidatos`}
        </Badge>
      )}
    </div>
  )
}

function DetailSection({ title, text }: { title: string; text: string }) {
  return (
    <section className="flex flex-col gap-2">
      <h3 className="text-xs font-semibold tracking-wider text-muted-foreground uppercase">{title}</h3>
      <FormattedText text={text} />
    </section>
  )
}

function VacancyDetailsDialog({
  vacancy,
  version,
  resume,
  isAdmin,
  onOpenChange,
  onApply,
}: {
  vacancy: Vacancy | null
  version: number
  resume?: Resume
  isAdmin: boolean
  onOpenChange: (open: boolean) => void
  onApply: (vacancy: Vacancy) => void
}) {
  return (
    <Dialog open={vacancy !== null} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92svh] gap-0 overflow-y-auto p-0 sm:max-w-2xl">
        {vacancy && (
          <div className="group/vacancy flex flex-col">
            <VacancyCover vacancy={vacancy} version={version} className="aspect-[21/9] w-full" />
            <div className="flex flex-col gap-6 p-6">
              <DialogHeader>
                <DialogTitle className="text-xl">{vacancy.name}</DialogTitle>
                <DialogDescription className="flex items-center gap-1.5">
                  <CalendarIcon className="size-3.5" />
                  Publicada em {formatDate(vacancy.created_at)}
                </DialogDescription>
              </DialogHeader>
              <VacancyMeta vacancy={vacancy} isAdmin={isAdmin} />
              <DetailSection title="Sobre a vaga" text={vacancy.description} />
              {vacancy.requirements && <DetailSection title="Requisitos" text={vacancy.requirements} />}
              {vacancy.responsibilities && <DetailSection title="Responsabilidades" text={vacancy.responsibilities} />}
              {resume && (
                <div className="flex flex-col gap-2">
                  <h3 className="text-sm font-medium">Seu andamento</h3>
                  <StageProgress stages={vacancy.stages} resume={resume} />
                </div>
              )}
              <div className="flex items-center justify-end border-t pt-5">
                {resume ? (
                  <StatusBadge status={resume.status} />
                ) : (
                  <Button
                    disabled={isExpired(vacancy)}
                    onClick={() => {
                      onOpenChange(false)
                      onApply(vacancy)
                    }}
                  >
                    <UploadIcon data-icon="inline-start" />
                    {isExpired(vacancy) ? "Vaga encerrada" : "Candidatar-se"}
                  </Button>
                )}
              </div>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

/** Escolha da foto da vaga, com pré-visualização. */
function PhotoPicker({
  vacancy,
  version,
  file,
  removed,
  onFile,
  onRemoved,
}: {
  vacancy: Vacancy | null
  version: number
  file: File | null
  removed: boolean
  onFile: (file: File | null) => void
  onRemoved: (removed: boolean) => void
}) {
  const [preview, setPreview] = useState<string | null>(null)

  useEffect(() => {
    if (!file) {
      setPreview(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  const existing = vacancy?.has_photo && !removed ? api.vacancies.photoUrl(vacancy.id, version) : null
  const shown = preview ?? existing

  function handle(chosen: File | undefined) {
    if (!chosen) return
    if (!PHOTO_TYPES.includes(chosen.type)) {
      toast.add({ title: "Formato não aceito", description: "Use uma imagem JPEG, PNG ou WebP.", type: "error" })
      return
    }
    if (chosen.size > PHOTO_MAX) {
      toast.add({ title: "Imagem grande demais", description: "A foto pode ter no máximo 2 MB.", type: "error" })
      return
    }
    onRemoved(false)
    onFile(chosen)
  }

  return (
    <Field>
      <FieldLabel htmlFor="vacancy-photo">Foto da vaga (opcional)</FieldLabel>
      <div className="w-full max-w-sm overflow-hidden rounded-lg border border-dashed bg-muted/40">
        {shown ? (
          <img src={shown} alt="Pré-visualização da foto da vaga" className="aspect-[16/9] w-full object-cover" />
        ) : (
          <div className="flex aspect-[16/9] flex-col items-center justify-center gap-2 text-muted-foreground">
            <ImageIcon className="size-8" />
            <span className="text-xs">JPEG, PNG ou WebP, até 2 MB</span>
          </div>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Input
          id="vacancy-photo"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="max-w-xs"
          onChange={(e) => {
            handle(e.target.files?.[0])
            e.target.value = ""
          }}
        />
        {shown && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => {
              onFile(null)
              if (vacancy?.has_photo) onRemoved(true)
            }}
          >
            Remover foto
          </Button>
        )}
      </div>
    </Field>
  )
}

function VacancyFormDialog({
  vacancy,
  version,
  open,
  onOpenChange,
  onSaved,
}: {
  vacancy: Vacancy | null
  version: number
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [requirements, setRequirements] = useState("")
  const [responsibilities, setResponsibilities] = useState("")
  const [stages, setStages] = useState<string[]>(DEFAULT_STAGES)
  const [expires, setExpires] = useState("")
  const [file, setFile] = useState<File | null>(null)
  const [removed, setRemoved] = useState(false)
  const [busy, setBusy] = useState(false)
  const [lastOpen, setLastOpen] = useState(false)

  // Preenche o formulário toda vez que o diálogo abre.
  if (open !== lastOpen) {
    setLastOpen(open)
    if (open) {
      setName(vacancy?.name ?? "")
      setDescription(vacancy?.description ?? "")
      setRequirements(vacancy?.requirements ?? "")
      setResponsibilities(vacancy?.responsibilities ?? "")
      setStages(vacancy?.stages ?? DEFAULT_STAGES)
      setExpires(toDateInput(vacancy?.expires_at))
      setFile(null)
      setRemoved(false)
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      // O prazo só vai quando mudou (editar uma vaga vencida sem mexer na data não pode falhar): vazio remove.
      const original = toDateInput(vacancy?.expires_at)
      const expiresAt = expires === original ? undefined : expires ? endOfDayIso(expires) : ""
      const data = { name, description, requirements, responsibilities, stages, expiresAt } // vazio em requisitos/responsabilidades apaga
      const saved = vacancy ? await api.vacancies.update(vacancy.id, data) : await api.vacancies.create(data)
      let photoProblem: string | null = null
      try {
        if (file) await api.vacancies.uploadPhoto(saved.id, file)
        else if (removed && vacancy?.has_photo) await api.vacancies.removePhoto(saved.id)
      } catch (error) {
        photoProblem = errorMessage(error)
      }
      if (photoProblem) {
        toast.add({ title: "Vaga salva, mas a foto não foi enviada", description: photoProblem, type: "error" })
      } else {
        toast.add({ title: vacancy ? "Vaga atualizada" : "Vaga publicada", type: "success" })
      }
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
      <DialogContent className="max-h-[92svh] overflow-y-auto sm:max-w-xl">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{vacancy ? "Editar vaga" : "Nova vaga"}</DialogTitle>
            <DialogDescription>
              Só a descrição é obrigatória. A IA usa tudo o que você preencher para avaliar os currículos.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="vacancy-name">Nome *</FieldLabel>
              <Input id="vacancy-name" required value={name} onChange={(e) => setName(e.target.value)} />
            </Field>
            <PhotoPicker
              vacancy={vacancy}
              version={version}
              file={file}
              removed={removed}
              onFile={setFile}
              onRemoved={setRemoved}
            />
            <Field>
              <FieldLabel htmlFor="vacancy-description">Descrição *</FieldLabel>
              <Textarea
                id="vacancy-description"
                rows={4}
                required
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
              <FieldDescription>Apresente a vaga e a área em poucas linhas.</FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="vacancy-requirements">Requisitos (opcional)</FieldLabel>
              <Textarea
                id="vacancy-requirements"
                rows={4}
                value={requirements}
                onChange={(e) => setRequirements(e.target.value)}
              />
              <FieldDescription>Um por linha: o que a pessoa precisa ter ou saber.</FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="vacancy-responsibilities">Responsabilidades (opcional)</FieldLabel>
              <Textarea
                id="vacancy-responsibilities"
                rows={4}
                value={responsibilities}
                onChange={(e) => setResponsibilities(e.target.value)}
              />
              <FieldDescription>Uma por linha: o que a pessoa vai fazer no dia a dia.</FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="vacancy-expires">Expiração (opcional)</FieldLabel>
              <Input
                id="vacancy-expires"
                type="date"
                value={expires}
                min={vacancy ? undefined : toDateInput(new Date().toISOString())}
                onChange={(e) => setExpires(e.target.value)}
              />
              <FieldDescription>
                Até esse dia (inclusive) a vaga recebe candidaturas. Em branco, ela fica aberta até você excluí-la.
              </FieldDescription>
            </Field>
            <Field>
              <FieldLabel>Etapas do processo</FieldLabel>
              <StageEditor stages={stages} onChange={setStages} />
              <FieldDescription>
                Só a Triagem (análise do currículo) é obrigatória. O candidato é avisado por e-mail a cada etapa.
              </FieldDescription>
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
            <FieldLabel htmlFor="resume-file">Currículo (PDF ou DOCX)</FieldLabel>
            <Input
              id="resume-file"
              type="file"
              accept="application/pdf,.pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.docx"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            <FieldDescription>Até 5 MB. Você pode se candidatar uma vez por vaga.</FieldDescription>
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
  const [viewing, setViewing] = useState<Vacancy | null>(null)
  const [deleting, setDeleting] = useState<Vacancy | null>(null)
  const [photoVersion, setPhotoVersion] = useState(() => Date.now()) // muda a cada edição: busca a foto de novo

  const { data, reload } = useQuery(async () => {
    const [vacancies, resumes] = await Promise.all([api.vacancies.list(), api.resumes.listMine()])
    return { vacancies, resumes }
  })

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (data?.vacancies ?? []).filter(
      (v) =>
        !term ||
        [v.name, v.description, v.requirements, v.responsibilities].some((text) => text?.toLowerCase().includes(term)),
    )
  }, [data, search])

  const myResume = (vacancyId: number) => data?.resumes.find((r) => r.vacancy_id === vacancyId)

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
        <div className="mt-2 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-80" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <Empty className="mt-2 border">
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
        <div className="mt-2 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((vacancy) => {
            const resume = myResume(vacancy.id)
            return (
              <Card
                key={vacancy.id}
                className="group/vacancy pt-0 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-xl hover:ring-foreground/20"
              >
                <button
                  type="button"
                  onClick={() => setViewing(vacancy)}
                  className="block w-full cursor-pointer text-left outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  aria-label={`Ver detalhes de ${vacancy.name}`}
                >
                  <VacancyCover vacancy={vacancy} version={photoVersion} className="aspect-[16/9] w-full" />
                </button>
                <CardHeader>
                  <CardTitle className="line-clamp-2 text-base">{vacancy.name}</CardTitle>
                  <CardDescription className="flex items-center gap-1.5">
                    <CalendarIcon className="size-3.5" />
                    Publicada em {formatDate(vacancy.created_at)}
                  </CardDescription>
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
                <CardContent className="flex flex-1 flex-col gap-3">
                  <p className="line-clamp-3 text-sm text-muted-foreground">{vacancy.description}</p>
                  <VacancyMeta vacancy={vacancy} isAdmin={isAdmin} />
                  {(vacancy.requirements || vacancy.responsibilities) && (
                    <div className="flex flex-wrap gap-1.5">
                      {vacancy.requirements && <Badge variant="secondary">Requisitos</Badge>}
                      {vacancy.responsibilities && <Badge variant="secondary">Responsabilidades</Badge>}
                    </div>
                  )}
                </CardContent>
                <CardFooter className="gap-2">
                  <Button variant="ghost" onClick={() => setViewing(vacancy)}>
                    Ver detalhes
                  </Button>
                  {resume ? (
                    <span className="ml-auto">
                      <StatusBadge status={resume.status} />
                    </span>
                  ) : (
                    <Button
                      variant="outline"
                      className="ml-auto"
                      disabled={isExpired(vacancy)}
                      onClick={() => setApplying(vacancy)}
                    >
                      <UploadIcon data-icon="inline-start" />
                      {isExpired(vacancy) ? "Vaga encerrada" : "Candidatar-se"}
                    </Button>
                  )}
                </CardFooter>
              </Card>
            )
          })}
        </div>
      )}

      <VacancyFormDialog
        vacancy={editing}
        version={photoVersion}
        open={formOpen}
        onOpenChange={setFormOpen}
        onSaved={() => {
          setPhotoVersion(Date.now())
          reload()
        }}
      />
      <VacancyDetailsDialog
        vacancy={viewing}
        version={photoVersion}
        resume={viewing ? myResume(viewing.id) : undefined}
        isAdmin={isAdmin}
        onOpenChange={(open) => !open && setViewing(null)}
        onApply={setApplying}
      />
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
