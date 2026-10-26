import { useState, type FormEvent } from "react"
import { useNavigate } from "react-router-dom"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldContent, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { toast } from "@/components/ui/toast"
import { PageHeader } from "@/components/PageHeader"
import { api, errorMessage } from "@/lib/api"
import { useAuth, useCurrentUser } from "@/lib/auth"
import { formatDate, initials } from "@/lib/format"
import { GENDER_LABEL, type Gender } from "@/lib/types"

const GENDER_ITEMS = Object.entries(GENDER_LABEL).map(([value, label]) => ({ value, label }))

function PersonalData() {
  const user = useCurrentUser()
  const { setUser } = useAuth()
  const [age, setAge] = useState(user.age ? String(user.age) : "")
  const [gender, setGender] = useState<Gender | null>(user.gender)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      let updated = user
      if (age && Number(age) !== user.age) updated = await api.users.update("age", Number(age))
      if (gender && gender !== user.gender) updated = await api.users.update("gender", gender)
      setUser(updated)
      toast.add({ title: "Dados atualizados", type: "success" })
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <form onSubmit={handleSubmit} className="flex flex-col gap-6">
        <CardHeader>
          <CardTitle>Dados pessoais</CardTitle>
          <CardDescription>Nome, e-mail e CPF não podem ser alterados.</CardDescription>
        </CardHeader>
        <CardContent>
          <FieldGroup>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-disabled>
                <FieldLabel htmlFor="profile-name">Nome</FieldLabel>
                <Input id="profile-name" value={user.name} disabled />
              </Field>
              <Field data-disabled>
                <FieldLabel htmlFor="profile-email">E-mail</FieldLabel>
                <Input id="profile-email" value={user.email} disabled />
              </Field>
              <Field data-disabled>
                <FieldLabel htmlFor="profile-cpf">CPF</FieldLabel>
                <Input id="profile-cpf" value={user.cpf ?? "Não informado"} disabled />
              </Field>
              <Field>
                <FieldLabel htmlFor="profile-age">Idade</FieldLabel>
                <Input id="profile-age" type="number" min={18} value={age} onChange={(e) => setAge(e.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="profile-gender">Gênero</FieldLabel>
                <Select items={GENDER_ITEMS} value={gender} onValueChange={(value) => setGender(value as Gender)}>
                  <SelectTrigger id="profile-gender" className="w-full">
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
              </Field>
            </div>
          </FieldGroup>
        </CardContent>
        <CardFooter>
          <Button type="submit" disabled={busy}>
            {busy && <Spinner data-icon="inline-start" />}
            Salvar alterações
          </Button>
        </CardFooter>
      </form>
    </Card>
  )
}

function Security() {
  const user = useCurrentUser()
  const { setUser } = useAuth()
  const [current, setCurrent] = useState("")
  const [next, setNext] = useState("")
  const [busy, setBusy] = useState(false)

  async function toggleTwoFactor(checked: boolean) {
    try {
      setUser(await api.users.update("permission", checked))
      toast.add({
        title: checked ? "Verificação em duas etapas ativada" : "Verificação em duas etapas desativada",
        type: "success",
      })
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    }
  }

  async function handlePassword(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      await api.auth.changePassword(current, next)
      setCurrent("")
      setNext("")
      toast.add({ title: "Senha alterada", type: "success" })
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Verificação em duas etapas</CardTitle>
          <CardDescription>Ao entrar, pediremos um código enviado para o seu e-mail.</CardDescription>
        </CardHeader>
        <CardContent>
          <Field orientation="horizontal">
            <FieldContent>
              <FieldLabel htmlFor="two-factor">Código por e-mail no login</FieldLabel>
              <FieldDescription>
                {user.provider === "google" ? "Contas Google já são verificadas pelo Google." : user.email}
              </FieldDescription>
            </FieldContent>
            <Switch
              id="two-factor"
              checked={user.permission}
              disabled={user.provider === "google"}
              onCheckedChange={toggleTwoFactor}
            />
          </Field>
        </CardContent>
      </Card>

      {user.provider === "local" && (
        <Card>
          <form onSubmit={handlePassword} className="flex flex-col gap-6">
            <CardHeader>
              <CardTitle>Alterar senha</CardTitle>
              <CardDescription>Mínimo de 8 caracteres, com maiúscula, minúscula e número.</CardDescription>
            </CardHeader>
            <CardContent>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="current-password">Senha atual</FieldLabel>
                  <Input
                    id="current-password"
                    type="password"
                    autoComplete="current-password"
                    value={current}
                    onChange={(e) => setCurrent(e.target.value)}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="new-password">Nova senha</FieldLabel>
                  <Input
                    id="new-password"
                    type="password"
                    autoComplete="new-password"
                    value={next}
                    onChange={(e) => setNext(e.target.value)}
                  />
                </Field>
              </FieldGroup>
            </CardContent>
            <CardFooter>
              <Button type="submit" disabled={busy}>
                {busy && <Spinner data-icon="inline-start" />}
                Alterar senha
              </Button>
            </CardFooter>
          </form>
        </Card>
      )}
    </div>
  )
}

function DangerZone() {
  const { setUser } = useAuth()
  const navigate = useNavigate()
  const [password, setPassword] = useState("")

  async function handleDelete() {
    try {
      await api.users.deleteMe(password)
      setUser(null)
      navigate("/login")
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Excluir conta</CardTitle>
        <CardDescription>Apaga sua conta e todas as suas candidaturas. Não dá para desfazer.</CardDescription>
      </CardHeader>
      <CardFooter>
        <AlertDialog onOpenChange={() => setPassword("")}>
          <AlertDialogTrigger render={<Button variant="destructive" />}>Excluir minha conta</AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Tem certeza?</AlertDialogTitle>
              <AlertDialogDescription>Confirme sua senha para excluir a conta.</AlertDialogDescription>
            </AlertDialogHeader>
            <Field>
              <FieldLabel htmlFor="delete-password">Senha</FieldLabel>
              <Input
                id="delete-password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </Field>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancelar</AlertDialogCancel>
              <AlertDialogAction variant="destructive" onClick={handleDelete}>
                Excluir conta
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </CardFooter>
    </Card>
  )
}

export default function Profile() {
  const user = useCurrentUser()

  return (
    <>
      <PageHeader title="Perfil" description="Seus dados, segurança e conta." />
      <Card>
        <CardHeader className="flex flex-row items-center gap-4">
          <Avatar className="size-12">
            <AvatarFallback>{initials(user.name)}</AvatarFallback>
          </Avatar>
          <div className="flex min-w-0 flex-col gap-1">
            <CardTitle className="truncate">{user.name}</CardTitle>
            <CardDescription className="truncate">
              {user.email} · desde {formatDate(user.created_at)}
            </CardDescription>
          </div>
          <div className="ml-auto flex gap-2">
            <Badge variant="secondary">{user.role === "admin" ? "Admin" : "Candidato"}</Badge>
            <Badge variant="outline">{user.provider === "google" ? "Google" : "E-mail"}</Badge>
          </div>
        </CardHeader>
      </Card>
      <Tabs defaultValue="dados">
        <TabsList>
          <TabsTrigger value="dados">Dados</TabsTrigger>
          <TabsTrigger value="seguranca">Segurança</TabsTrigger>
          <TabsTrigger value="conta">Conta</TabsTrigger>
        </TabsList>
        <TabsContent value="dados">
          <PersonalData />
        </TabsContent>
        <TabsContent value="seguranca">
          <Security />
        </TabsContent>
        <TabsContent value="conta">
          <DangerZone />
        </TabsContent>
      </Tabs>
    </>
  )
}
