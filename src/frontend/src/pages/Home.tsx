import { useNavigate } from "react-router-dom"
import { LogOutIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { useAuth } from "@/lib/auth"

export default function Home() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate("/login")
  }

  return (
    <div className="mx-auto flex min-h-svh w-full max-w-3xl flex-col gap-6 px-6 py-10">
      <header className="flex items-center justify-between">
        <span className="text-lg font-semibold">RhHub</span>
        <Button variant="outline" onClick={handleLogout}>
          <LogOutIcon data-icon="inline-start" />
          Sair
        </Button>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>Olá, {user?.name}!</CardTitle>
          <CardDescription>{user?.email}</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center gap-2">
          <Badge variant="secondary">{user?.provider === "google" ? "Conta Google" : "Conta local"}</Badge>
          <span className="text-sm text-muted-foreground">
            Login simulado. Vagas e currículos chegam nas próximas etapas.
          </span>
        </CardContent>
      </Card>
    </div>
  )
}
