import { useState, type FormEvent } from "react"
import { CodeInput } from "@/components/CodeInput"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { api, errorMessage } from "@/lib/api"

export function ForgotPasswordDialog({ defaultEmail }: { defaultEmail: string }) {
  const [open, setOpen] = useState(false)
  const [step, setStep] = useState<"email" | "reset">("email")
  const [email, setEmail] = useState(defaultEmail)
  const [code, setCode] = useState("")
  const [password, setPassword] = useState("")
  const [busy, setBusy] = useState(false)

  function handleOpenChange(next: boolean) {
    setOpen(next)
    if (next) {
      setStep("email")
      setEmail(defaultEmail)
      setCode("")
      setPassword("")
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      if (step === "email") {
        const { code: sent } = await api.auth.sendPasswordResetCode(email)
        toast.add({ title: "Código enviado", description: `Simulação: o código é ${sent}.` })
        setStep("reset")
      } else {
        await api.auth.resetPassword(email, code, password)
        toast.add({ title: "Senha alterada", description: "Entre com a nova senha.", type: "success" })
        setOpen(false)
      }
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={<Button type="button" variant="link" size="sm" className="h-auto p-0" />}>
        Esqueci a senha
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Recuperar senha</DialogTitle>
            <DialogDescription>
              {step === "email"
                ? "Enviaremos um código para o seu e-mail."
                : "Digite o código recebido e a nova senha."}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            {step === "email" ? (
              <Field>
                <FieldLabel htmlFor="reset-email">E-mail</FieldLabel>
                <Input id="reset-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
              </Field>
            ) : (
              <>
                <Field>
                  <FieldLabel htmlFor="reset-code">Código</FieldLabel>
                  <CodeInput id="reset-code" value={code} onChange={setCode} />
                </Field>
                <Field>
                  <FieldLabel htmlFor="reset-password">Nova senha</FieldLabel>
                  <Input
                    id="reset-password"
                    type="password"
                    autoComplete="new-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                </Field>
              </>
            )}
          </FieldGroup>
          <DialogFooter>
            <Button type="submit" disabled={busy}>
              {busy && <Spinner data-icon="inline-start" />}
              {step === "email" ? "Enviar código" : "Alterar senha"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
