import { useState } from "react"
import { MoreHorizontalIcon, ShieldIcon, ShieldOffIcon, Trash2Icon, UsersIcon } from "lucide-react"
import { PageHeader } from "@/components/PageHeader"
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
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { toast } from "@/components/ui/toast"
import { api, errorMessage } from "@/lib/api"
import { useCurrentUser } from "@/lib/auth"
import { formatDate, initials } from "@/lib/format"
import type { User } from "@/lib/types"
import { useQuery } from "@/lib/use-query"

export default function AdminUsers() {
  const me = useCurrentUser()
  const { data: users, reload } = useQuery(() => api.admin.listUsers())
  const [deleting, setDeleting] = useState<User | null>(null)

  async function toggleRole(user: User) {
    const role = user.role === "admin" ? "user" : "admin"
    try {
      await api.admin.setRole(user.public_id, role)
      toast.add({ title: role === "admin" ? `${user.name} agora é admin` : `${user.name} voltou a ser candidato`, type: "success" })
      reload()
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    }
  }

  async function confirmDelete() {
    if (!deleting) return
    try {
      await api.admin.deleteUser(deleting.public_id)
      toast.add({ title: "Usuário excluído", type: "success" })
      setDeleting(null)
      reload()
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    }
  }

  return (
    <>
      <PageHeader title="Usuários" description="Promova administradores e gerencie contas." />

      {!users ? (
        <Skeleton className="h-48 w-full" />
      ) : users.length === 0 ? (
        <Empty className="border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <UsersIcon />
            </EmptyMedia>
            <EmptyTitle>Nenhum usuário</EmptyTitle>
          </EmptyHeader>
        </Empty>
      ) : (
        <Card className="py-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Usuário</TableHead>
                <TableHead>Papel</TableHead>
                <TableHead className="hidden md:table-cell">Login</TableHead>
                <TableHead className="hidden md:table-cell">Desde</TableHead>
                <TableHead className="w-10">
                  <span className="sr-only">Ações</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.map((user) => (
                <TableRow key={user.public_id}>
                  <TableCell>
                    <div className="flex items-center gap-3">
                      <Avatar className="size-8">
                        <AvatarFallback>{initials(user.name)}</AvatarFallback>
                      </Avatar>
                      <div className="flex min-w-0 flex-col">
                        <span className="truncate font-medium">
                          {user.name}
                          {user.public_id === me.public_id && " (você)"}
                        </span>
                        <span className="truncate text-xs text-muted-foreground">{user.email}</span>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={user.role === "admin" ? "default" : "secondary"}>
                      {user.role === "admin" ? "Admin" : "Candidato"}
                    </Badge>
                  </TableCell>
                  <TableCell className="hidden text-muted-foreground md:table-cell">
                    {user.provider === "google" ? "Google" : "E-mail e senha"}
                  </TableCell>
                  <TableCell className="hidden text-muted-foreground md:table-cell">
                    {formatDate(user.created_at)}
                  </TableCell>
                  <TableCell>
                    <DropdownMenu>
                      <DropdownMenuTrigger
                        render={<Button variant="ghost" size="icon-sm" disabled={user.public_id === me.public_id} />}
                      >
                        <MoreHorizontalIcon />
                        <span className="sr-only">Ações</span>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuGroup>
                          <DropdownMenuItem onClick={() => toggleRole(user)}>
                            {user.role === "admin" ? <ShieldOffIcon /> : <ShieldIcon />}
                            {user.role === "admin" ? "Remover admin" : "Tornar admin"}
                          </DropdownMenuItem>
                        </DropdownMenuGroup>
                        <DropdownMenuSeparator />
                        <DropdownMenuGroup>
                          <DropdownMenuItem variant="destructive" onClick={() => setDeleting(user)}>
                            <Trash2Icon />
                            Excluir conta
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
      )}

      <AlertDialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Excluir {deleting?.name}?</AlertDialogTitle>
            <AlertDialogDescription>
              A conta e as candidaturas desse usuário serão apagadas. Essa ação não pode ser desfeita.
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
