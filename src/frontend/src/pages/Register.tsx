import { useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router-dom"
import { ArrowLeftIcon } from "lucide-react"
import { AuthLayout } from "@/components/AuthLayout"
import { CodeInput } from "@/components/CodeInput"
import { GoogleButton } from "@/components/GoogleButton"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldGroup, FieldLabel, FieldSeparator } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { api, errorMessage } from "@/lib/api"
import { useAuth } from "@/lib/auth"
import { GENDER_LABEL, type Gender } from "@/lib/types"

const GENDER_ITEMS = Object.entries(GENDER_LABEL).map(([value, label]) => ({ value, label }))

export default function Register() {
  const { setUser } = useAuth()
  const navigate = useNavigate()
  const [step, setStep] = useState<"form" | "code">("form")
  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [cpf, setCpf] = useState("")
  const [age, setAge] = useState("")
  const [gender, setGender] = useState<Gender | null>(null)
  const [password, setPassword] = useState("")
  const [code, setCode] = useState("")
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    try {
      await action()
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    } finally {
      setBusy(false)
    }
  }

  function handleForm(event: FormEvent) {
    event.preventDefault()
    run(async () => {
      const { code: sent } = await api.auth.sendSignupCode(name, email)
      toast.add({ title: "Código enviado para o seu e-mail", description: `Simulação: o código é ${sent}.` })
      setStep("code")
    })
  }

  function handleCode(event: FormEvent) {
    event.preventDefault()
    run(async () => {
      const user = await api.auth.signup(
        { name, email, cpf, age: age ? Number(age) : undefined, gender: gender ?? undefined, password },
        code,
      )
      setUser(user)
      toast.add({ title: "Conta criada", description: `Bem-vindo, ${user.name}!`, type: "success" })
      navigate("/")
    })
  }

  function handleGoogle() {
    run(async () => {
      setUser(await api.auth.loginWithGoogle())
      navigate("/")
    })
  }

  if (step === "code") {
    return (
      <AuthLayout>
        <Card className="rh-auth-card w-full max-w-sm backdrop-blur">
          <CardHeader>
            <CardTitle>Confirme seu e-mail</CardTitle>
            <CardDescription>Digite o código enviado para {email || "o seu e-mail"}. Ele vale por 15 minutos.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCode}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="code">Código</FieldLabel>
                  <CodeInput id="code" value={code} onChange={setCode} />
                </Field>
                <Button type="submit" size="lg" className="w-full" disabled={busy}>
                  {busy && <Spinner data-icon="inline-start" />}
                  Criar conta
                </Button>
              </FieldGroup>
            </form>
          </CardContent>
          <CardFooter className="justify-center">
            <Button variant="ghost" size="sm" onClick={() => setStep("form")}>
              <ArrowLeftIcon data-icon="inline-start" />
              Voltar
            </Button>
          </CardFooter>
        </Card>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <Card className="rh-auth-card w-full max-w-md backdrop-blur">
        <CardHeader>
          <CardTitle>Criar conta</CardTitle>
          <CardDescription>Cadastre-se para se candidatar às vagas.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleForm}>
            <FieldGroup>
              <GoogleButton onClick={handleGoogle}>Cadastrar com Google</GoogleButton>
              <FieldSeparator>ou</FieldSeparator>
              <Field>
                <FieldLabel htmlFor="name">Nome</FieldLabel>
                <Input id="name" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="email">E-mail</FieldLabel>
                <Input
                  id="email"
                  type="email"
                  placeholder="voce@gmail.com"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field>
                  <FieldLabel htmlFor="cpf">CPF</FieldLabel>
                  <Input
                    id="cpf"
                    inputMode="numeric"
                    maxLength={11}
                    placeholder="Somente números"
                    value={cpf}
                    onChange={(e) => setCpf(e.target.value.replace(/\D/g, ""))}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="age">Idade</FieldLabel>
                  <Input id="age" type="number" min={18} value={age} onChange={(e) => setAge(e.target.value)} />
                </Field>
              </div>
              <Field>
                <FieldLabel htmlFor="gender">Gênero</FieldLabel>
                <Select items={GENDER_ITEMS} value={gender} onValueChange={(value) => setGender(value as Gender)}>
                  <SelectTrigger id="gender" className="w-full">
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
              <Field>
                <FieldLabel htmlFor="password">Senha</FieldLabel>
                <Input
                  id="password"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <FieldDescription>Mínimo de 8 caracteres, com maiúscula, minúscula e número.</FieldDescription>
              </Field>
              <Button type="submit" size="lg" className="w-full" disabled={busy}>
                {busy && <Spinner data-icon="inline-start" />}
                Continuar
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
        <CardFooter className="justify-center text-sm text-muted-foreground">
          Já tem conta?&nbsp;
          <Link to="/login" className="text-foreground underline underline-offset-4">
            Entrar
          </Link>
        </CardFooter>
      </Card>
    </AuthLayout>
  )
}
