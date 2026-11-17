import { Link, Outlet, useLocation, useNavigate } from "react-router-dom"
import {
  ArrowUpRightIcon, BriefcaseBusinessIcon, ChevronDownIcon, ChevronsUpDownIcon,
  CircleHelpIcon, FileTextIcon, LayoutDashboardIcon, LogOutIcon,
  SearchIcon, Settings2Icon, ShieldCheckIcon, UserRoundIcon, UsersIcon,
  type LucideIcon,
} from "lucide-react"
import { Brand } from "@/components/Brand"
import { NotificationBell } from "@/components/NotificationBell"
import {
  Avatar, AvatarFallback, AvatarImage,
} from "@/components/ui/avatar"
import {
  Breadcrumb, BreadcrumbItem, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Button, buttonVariants } from "@/components/ui/button"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem,
  DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Separator } from "@/components/ui/separator"
import {
  Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent,
  SidebarGroupLabel, SidebarHeader, SidebarInset, SidebarMenu, SidebarMenuButton,
  SidebarMenuItem, SidebarProvider, SidebarRail, SidebarTrigger, useSidebar,
} from "@/components/ui/sidebar"
import { useAuth, useCurrentUser } from "@/lib/auth"
import { api } from "@/lib/api"
import { initials } from "@/lib/format"
import { cn } from "@/lib/utils"

type NavItem = { to: string; label: string; icon: LucideIcon }

const NAV: NavItem[] = [
  { to: "/", label: "Visão geral", icon: LayoutDashboardIcon },
  { to: "/vagas", label: "Explorar vagas", icon: BriefcaseBusinessIcon },
  { to: "/candidaturas", label: "Minhas candidaturas", icon: FileTextIcon },
  { to: "/perfil", label: "Meu perfil", icon: UserRoundIcon },
]

const ADMIN_NAV: NavItem[] = [
  { to: "/admin/usuarios", label: "Gerenciar usuários", icon: UsersIcon },
  { to: "/admin/candidaturas", label: "Todas as candidaturas", icon: ShieldCheckIcon },
]

const TITLES: Record<string, string> = {
  "/": "Visão geral",
  "/vagas": "Explorar vagas",
  "/candidaturas": "Minhas candidaturas",
  "/perfil": "Meu perfil",
  "/admin/usuarios": "Gerenciar usuários",
  "/admin/candidaturas": "Todas as candidaturas",
}

function UserAvatar({ className }: { className?: string }) {
  const user = useCurrentUser()
  const { photoVersion } = useAuth()
  return (
    <Avatar className={className}>
      {user.has_photo && <AvatarImage src={api.users.photoUrl(photoVersion)} alt="" />}
      <AvatarFallback className="bg-linear-to-br from-indigo-400 to-violet-500 text-xs font-bold text-white">
        {initials(user.name)}
      </AvatarFallback>
    </Avatar>
  )
}

function AppSidebar() {
  const user = useCurrentUser()
  const { logout } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { isMobile, setOpenMobile } = useSidebar()

  async function handleLogout() {
    await logout()
    navigate("/login")
  }

  function group(label: string, items: NavItem[]) {
    return (
      <SidebarGroup>
        <SidebarGroupLabel className="text-[10px] font-semibold tracking-widest uppercase">{label}</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            {items.map(({ to, label: name, icon: Icon }) => (
              <SidebarMenuItem key={to}>
                <SidebarMenuButton
                  isActive={pathname === to}
                  tooltip={name}
                  render={<Link to={to} onClick={() => setOpenMobile(false)} />}
                >
                  <Icon />
                  <span>{name}</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            ))}
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    )
  }

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader className="gap-3 p-3">
        <Link to="/" onClick={() => setOpenMobile(false)} aria-label="RhHub, ir para a visão geral" className="flex h-10 items-center rounded-lg px-1 outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring">
          <Brand className="group-data-[collapsible=icon]:[&>span:last-child]:hidden" />
        </Link>
        <div className="flex items-center gap-2.5 rounded-xl border border-sidebar-border bg-sidebar-accent/40 p-2.5 group-data-[collapsible=icon]:hidden">
          <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-sidebar-accent text-sidebar-primary">
            <BriefcaseBusinessIcon className="size-4" />
          </span>
          <div className="min-w-0 flex-1 leading-tight">
            <span className="block text-[9px] font-semibold tracking-widest text-sidebar-foreground/60">SEU WORKSPACE</span>
            <span className="block truncate text-[13px] font-semibold">Recrutamento</span>
          </div>
          <ChevronDownIcon className="size-4 shrink-0 opacity-50" />
        </div>
      </SidebarHeader>

      <SidebarContent>
        {group("Espaço de trabalho", NAV)}
        {user.role === "admin" && group("Administração", ADMIN_NAV)}
      </SidebarContent>

      <SidebarFooter className="gap-3 p-3">
        <div className="rounded-xl border border-sidebar-border bg-linear-to-br from-indigo-400/15 to-cyan-400/5 p-3.5 group-data-[collapsible=icon]:hidden [@media(max-height:700px)]:hidden">
          <span className="mb-2.5 grid size-8 place-items-center rounded-lg bg-indigo-400/25 text-indigo-200">
            <CircleHelpIcon className="size-4" />
          </span>
          <strong className="block text-[13px]">Precisa de ajuda?</strong>
          <p className="mt-1 mb-2.5 text-xs leading-relaxed text-sidebar-foreground/70">
            Explore os recursos e acompanhe suas oportunidades por aqui.
          </p>
          <Link to="/perfil" onClick={() => setOpenMobile(false)} className="inline-flex items-center gap-1.5 text-xs font-semibold text-cyan-300 hover:underline">
            Ver meu perfil <ArrowUpRightIcon className="size-3.5" />
          </Link>
        </div>

        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger render={<SidebarMenuButton size="lg" className="data-popup-open:bg-sidebar-accent" />}>
                <UserAvatar className="rounded-lg after:rounded-lg" />
                <div className="grid min-w-0 flex-1 text-left leading-tight">
                  <span className="truncate text-[13px] font-semibold">{user.name}</span>
                  <span className="truncate text-[11px] text-sidebar-foreground/60">
                    {user.role === "admin" ? "Administrador" : "Candidato"}
                  </span>
                </div>
                <ChevronsUpDownIcon className="ml-auto size-4 opacity-60" />
              </DropdownMenuTrigger>
              <DropdownMenuContent side={isMobile ? "top" : "right"} align="end" sideOffset={8} className="w-56">
                <DropdownMenuGroup>
                  <DropdownMenuLabel className="truncate">{user.email}</DropdownMenuLabel>
                </DropdownMenuGroup>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={() => { setOpenMobile(false); navigate("/perfil") }}>
                  <Settings2Icon /> Configurações
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleLogout}>
                  <LogOutIcon /> Sair
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}

function TopBar() {
  const user = useCurrentUser()
  const { logout } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate("/login")
  }

  return (
    <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-2 border-b bg-background/80 px-3 backdrop-blur-md sm:px-5">
      <SidebarTrigger className="-ml-1" aria-label="Minimizar ou expandir o menu lateral" title="Minimizar/expandir menu (Ctrl+B)" />
      <Separator orientation="vertical" className="mx-1 data-vertical:h-4 data-vertical:self-center" />
      <Breadcrumb className="min-w-0">
        <BreadcrumbList className="flex-nowrap">
          <BreadcrumbItem className="hidden sm:inline-flex">Workspace</BreadcrumbItem>
          <BreadcrumbSeparator className="hidden sm:block" />
          <BreadcrumbItem className="min-w-0">
            <BreadcrumbPage className="truncate font-medium">{TITLES[pathname] ?? "RhHub"}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <div className="ml-auto flex items-center gap-1.5">
        <Link to="/vagas" className={cn(buttonVariants({ variant: "outline" }), "hidden h-9 min-w-52 justify-start gap-2 px-3 font-normal text-muted-foreground md:inline-flex")}>
          <SearchIcon /> Buscar oportunidades
        </Link>
        <NotificationBell />
        <DropdownMenu>
          <DropdownMenuTrigger render={<Button variant="ghost" className="h-9 gap-1.5 rounded-full px-1 pr-2" aria-label="Opções da conta" />}>
            <UserAvatar className="size-7" />
            <ChevronDownIcon className="size-3.5 opacity-60" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" sideOffset={8} className="w-56">
            <DropdownMenuGroup>
              <DropdownMenuLabel className="truncate">{user.email}</DropdownMenuLabel>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => navigate("/perfil")}>
              <Settings2Icon /> Configurações
            </DropdownMenuItem>
            <DropdownMenuItem onClick={handleLogout}>
              <LogOutIcon /> Sair
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}

export function AppLayout() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="rh-main min-w-0">
        <TopBar />
        <div id="conteudo" className="mx-auto flex w-full max-w-[1600px] flex-1 flex-col gap-5 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <Outlet />
        </div>
        <footer className="flex flex-col gap-1 border-t px-4 py-4 text-xs text-muted-foreground sm:flex-row sm:justify-between sm:px-6 lg:px-8">
          <span>RhHub · Plataforma de recrutamento</span>
          <span>Seus dados ficam protegidos</span>
        </footer>
      </SidebarInset>
    </SidebarProvider>
  )
}
