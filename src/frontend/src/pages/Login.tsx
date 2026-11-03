import { useState, type FormEvent } from "react"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { ArrowLeftIcon } from "lucide-react"
import { AuthLayout } from "@/components/AuthLayout"
import { CodeInput } from "@/components/CodeInput"
import { ForgotPasswordDialog } from "@/components/ForgotPasswordDialog"
import { GoogleButton } from "@/components/GoogleButton"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldGroup, FieldLabel, FieldSeparator } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { api, errorMessage } from "@/lib/api"
import { useAuth } from "@/lib/auth"

/** Texto para cada motivo que o back end devolve em /login?error=google&reason=... (o código aparece junto, para suporte). */
function googleFailure(reason: string): string {
  const known: Record<string, string> = {
    state: "A sessão do login se perdeu no caminho. Tente de novo.",
    no_code: "O Google não devolveu a autorização. Tente de novo.",
    google_access_denied: "Você cancelou o login com o Google.",
    invalid_client: "O Google não reconheceu as credenciais deste app (chave secreta ou ID do cliente incorretos no servidor).",
    invalid_grant: "A autorização do Google expirou ou já foi usada. Tente de novo.",
    redirect_uri_mismatch: "O endereço de retorno do servidor não bate com o cadastrado no Google.",
    google_unreachable: "O servidor não conseguiu falar com o Google. Tente de novo em instantes.",
    not_gmail: "Só contas @gmail.com podem entrar.",
    email_not_verified: "O Google não verificou o e-mail dessa conta.",
  }
  if (known[reason]) return known[reason]
  if (reason.startsWith("server_error")) return "O servidor falhou ao concluir o login. Tente de novo; se repetir, avise o suporte."
  return "Não foi possível entrar com o Google."
}

export default function Login() {
  const { setUser } = useAuth()
  const navigate = useNavigate()
  const [step, setStep] = useState<"credentials" | "twoFactor">("credentials")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [code, setCode] = useState("")
  const [busy, setBusy] = useState(false)
  const [params] = useSearchParams()

  // O back end volta para /login?error=google&reason=... quando o login com Google não se completa.
  const googleReason = params.get("error") === "google" ? (params.get("reason") ?? "desconhecido") : null

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
        await api.auth.sendTwoFactorCode()
        toast.add({ title: "Código enviado para o seu e-mail", description: "Ele vale por 5 minutos." })
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
      setUser(await api.auth.verifyTwoFactor(code))
      navigate("/")
    })
  }

  function handleGoogle() {
    run(() => api.auth.startGoogleLogin())
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
          {googleReason && (
            <Alert variant="destructive" className="mb-4">
              <AlertTitle>Não foi possível entrar com o Google</AlertTitle>
              <AlertDescription>
                {googleFailure(googleReason)}
                <span className="mt-1 block text-xs opacity-70">Código: {googleReason}</span>
              </AlertDescription>
            </Alert>
          )}
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
                <FieldDescription>Use o e-mail @gmail.com do seu cadastro.</FieldDescription>
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
