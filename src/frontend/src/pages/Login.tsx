import { useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router-dom"
import { ArrowLeftIcon } from "lucide-react"
import { AuthLayout } from "@/components/AuthLayout"
import { CodeInput } from "@/components/CodeInput"
import { ForgotPasswordDialog } from "@/components/ForgotPasswordDialog"
import { GoogleButton } from "@/components/GoogleButton"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldGroup, FieldLabel, FieldSeparator } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { api, errorMessage } from "@/lib/api"
import { useAuth } from "@/lib/auth"

export default function Login() {
  const { setUser } = useAuth()
  const navigate = useNavigate()
  const [step, setStep] = useState<"credentials" | "twoFactor">("credentials")
  const [email, setEmail] = useState("")
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

  function handleCredentials(event: FormEvent) {
    event.preventDefault()
    run(async () => {
      const result = await api.auth.login(email, password)
      if (result.twoFactor) {
        const { code: sent } = await api.auth.sendTwoFactorCode(email)
        toast.add({ title: "Código enviado para o seu e-mail", description: `Simulação: o código é ${sent}.` })
        setStep("twoFactor")
        return
      }
      setUser(result.user)
      navigate("/")
    })
  }

  function handleTwoFactor(event: FormEvent) {
    event.preventDefault()
    run(async () => {
      setUser(await api.auth.verifyTwoFactor(email, code))
      navigate("/")
    })
  }

  function handleGoogle() {
    run(async () => {
      setUser(await api.auth.loginWithGoogle())
      navigate("/")
    })
  }

  if (step === "twoFactor") {
    return (
      <AuthLayout>
        <Card className="w-full max-w-sm bg-card/80 backdrop-blur">
          <CardHeader>
            <CardTitle>Verificação em duas etapas</CardTitle>
            <CardDescription>Digite o código de 6 dígitos enviado para {email || "o seu e-mail"}.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleTwoFactor}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="code">Código</FieldLabel>
                  <CodeInput id="code" value={code} onChange={setCode} />
                </Field>
                <Button type="submit" size="lg" className="w-full" disabled={busy}>
                  {busy && <Spinner data-icon="inline-start" />}
                  Verificar
                </Button>
              </FieldGroup>
            </form>
          </CardContent>
          <CardFooter className="justify-center">
            <Button variant="ghost" size="sm" onClick={() => setStep("credentials")}>
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
      <Card className="w-full max-w-sm bg-card/80 backdrop-blur">
        <CardHeader>
          <CardTitle>Entrar</CardTitle>
          <CardDescription>Acesse sua conta para acompanhar vagas e candidaturas.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleCredentials}>
            <FieldGroup>
              <GoogleButton onClick={handleGoogle}>Continuar com Google</GoogleButton>
              <FieldSeparator>ou</FieldSeparator>
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
                <FieldDescription>Na demonstração, e-mails com "admin" entram como administrador.</FieldDescription>
              </Field>
              <Field>
                <div className="flex items-center justify-between">
                  <FieldLabel htmlFor="password">Senha</FieldLabel>
                  <ForgotPasswordDialog defaultEmail={email} />
                </div>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </Field>
              <Button type="submit" size="lg" className="w-full" disabled={busy}>
                {busy && <Spinner data-icon="inline-start" />}
                Entrar
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
        <CardFooter className="justify-center text-sm text-muted-foreground">
          Não tem conta?&nbsp;
          <Link to="/cadastro" className="text-foreground underline underline-offset-4">
            Criar conta
          </Link>
        </CardFooter>
      </Card>
    </AuthLayout>
  )
}
