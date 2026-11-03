import { useRef, useState, type FormEvent } from "react"
import { Navigate, useNavigate } from "react-router-dom"
import { CameraIcon, CheckIcon } from "lucide-react"
import { AuthLayout } from "@/components/AuthLayout"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { api, errorMessage } from "@/lib/api"
import { useAuth, useCurrentUser } from "@/lib/auth"
import { initials } from "@/lib/format"
import { GENDER_LABEL, isProfileIncomplete, type Gender } from "@/lib/types"

const GENDER_ITEMS = Object.entries(GENDER_LABEL).map(([value, label]) => ({ value, label }))
const PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"]
const PHOTO_MAX = 2 * 1024 * 1024

type Step = "data" | "photo"

function Steps({ step }: { step: Step }) {
  const items: { id: Step; label: string }[] = [
    { id: "data", label: "Seus dados" },
    { id: "photo", label: "Foto" },
  ]
  return (
    <ol className="flex items-center gap-2 text-xs">
      {items.map((item, index) => {
        const done = step === "photo" && item.id === "data"
        const active = item.id === step
        return (
          <li key={item.id} className="flex items-center gap-2">
            <span
              className={`flex size-5 items-center justify-center rounded-full border text-[11px] ${
                active ? "border-primary bg-primary text-primary-foreground" : done ? "border-primary" : "text-muted-foreground"
              }`}
            >
              {done ? <CheckIcon className="size-3" /> : index + 1}
            </span>
            <span className={active ? "font-medium" : "text-muted-foreground"}>{item.label}</span>
            {index === 0 && <span className="text-muted-foreground">›</span>}
          </li>
        )
      })}
    </ol>
  )
}

/**
 * Primeiro acesso de quem entrou com o Google: aba 1 pede CPF, idade e gênero (obrigatórios); aba 2 deixa enviar uma
 * foto. Quem já tem o cadastro completo e abre a página por engano volta para o início.
 */
export default function CompleteProfile() {
  const user = useCurrentUser()
  const { setUser, bumpPhoto } = useAuth()
  const navigate = useNavigate()
  const [needsData] = useState(() => isProfileIncomplete(user)) // completar o cadastro aqui não pode expulsar da página
  const [step, setStep] = useState<Step>("data")
  const [cpf, setCpf] = useState(user.cpf ?? "")
  const [age, setAge] = useState(user.age != null ? String(user.age) : "")
  const [gender, setGender] = useState<Gender | null>(user.gender)
  const [busy, setBusy] = useState(false)
  const [preview, setPreview] = useState<string | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const input = useRef<HTMLInputElement>(null)

  if (!needsData && step === "data") return <Navigate to="/" replace />

  async function handleData(event: FormEvent) {
    event.preventDefault()
    if (!gender) {
      toast.add({ title: "Informe o gênero", description: "CPF, idade e gênero são obrigatórios.", type: "error" })
      return
    }
    setBusy(true)
    try {
      setUser(await api.users.completeProfile({ cpf, age: Number(age), gender }))
      setStep("photo")
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    } finally {
      setBusy(false)
    }
  }

  function choose(chosen: File | undefined) {
    if (!chosen) return
    if (!PHOTO_TYPES.includes(chosen.type)) {
      toast.add({ title: "Formato não aceito", description: "Use uma imagem JPEG, PNG ou WebP.", type: "error" })
      return
    }
    if (chosen.size > PHOTO_MAX) {
      toast.add({ title: "Imagem grande demais", description: "A foto pode ter no máximo 2 MB.", type: "error" })
      return
    }
    if (preview) URL.revokeObjectURL(preview)
    setFile(chosen)
    setPreview(URL.createObjectURL(chosen))
  }

  async function finish() {
    setBusy(true)
    try {
      if (file) {
        await api.users.uploadPhoto(file)
        setUser(await api.auth.me())
        bumpPhoto()
      }
      toast.add({ title: "Tudo pronto", description: `Bem-vindo, ${user.name}!`, type: "success" })
      navigate("/", { replace: true })
    } catch (error) {
      toast.add({ title: "Não foi possível enviar a foto", description: errorMessage(error), type: "error" })
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout>
      <Card className="w-full max-w-sm bg-card/80 backdrop-blur">
        <CardHeader>
          <Steps step={step} />
          <CardTitle>{step === "data" ? "Complete seu cadastro" : "Adicione uma foto"}</CardTitle>
          <CardDescription>
            {step === "data"
              ? "Para continuar, informe os dados abaixo. Todos são obrigatórios."
              : "Ela aparece no seu perfil. Você pode pular e enviar depois."}
          </CardDescription>
        </CardHeader>
        {step === "data" ? (
          <form onSubmit={handleData}>
            <CardContent>
              <FieldGroup>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field>
                    <FieldLabel htmlFor="cp-cpf">CPF</FieldLabel>
                    <Input
                      id="cp-cpf"
                      inputMode="numeric"
                      required
                      maxLength={11}
                      placeholder="Somente números"
                      value={cpf}
                      disabled={!!user.cpf}
                      onChange={(e) => setCpf(e.target.value.replace(/\D/g, ""))}
                    />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="cp-age">Idade</FieldLabel>
                    <Input id="cp-age" type="number" min={18} required value={age} onChange={(e) => setAge(e.target.value)} />
                  </Field>
                </div>
                <Field>
                  <FieldLabel htmlFor="cp-gender">Gênero</FieldLabel>
                  <Select items={GENDER_ITEMS} value={gender} onValueChange={(value) => setGender(value as Gender)}>
                    <SelectTrigger id="cp-gender" className="w-full">
                      <SelectValue placeholder="Selecione" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {GENDER_ITEMS.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                  <FieldDescription>O CPF não poderá ser alterado depois.</FieldDescription>
                </Field>
              </FieldGroup>
            </CardContent>
            <CardFooter className="pt-4">
              <Button type="submit" size="lg" className="w-full" disabled={busy}>
                {busy && <Spinner data-icon="inline-start" />}
                Continuar
              </Button>
            </CardFooter>
          </form>
        ) : (
          <>
            <CardContent className="flex flex-col items-center gap-4">
              <Avatar className="size-24">
                {preview && <AvatarImage src={preview} alt="" />}
                <AvatarFallback className="text-2xl">{initials(user.name)}</AvatarFallback>
              </Avatar>
              <input
                ref={input}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="hidden"
                onChange={(e) => choose(e.target.files?.[0])}
              />
              <Button type="button" variant="outline" disabled={busy} onClick={() => input.current?.click()}>
                <CameraIcon data-icon="inline-start" />
                {file ? "Trocar foto" : "Escolher foto"}
              </Button>
              <p className="text-xs text-muted-foreground">JPEG, PNG ou WebP, até 2 MB.</p>
            </CardContent>
            <CardFooter className="flex-col gap-2 pt-4">
              <Button size="lg" className="w-full" disabled={busy} onClick={finish}>
                {busy && <Spinner data-icon="inline-start" />}
                {file ? "Salvar e entrar" : "Entrar"}
              </Button>
              {!file && (
                <Button variant="ghost" size="sm" disabled={busy} onClick={finish}>
                  Pular por enquanto
                </Button>
              )}
            </CardFooter>
          </>
        )}
      </Card>
    </AuthLayout>
  )
}
