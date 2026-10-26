import { useEffect, useLayoutEffect, useRef, useState } from "react"
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom"
import {
  ArrowUpRightIcon, BellIcon, BriefcaseBusinessIcon, ChevronDownIcon,
  CircleHelpIcon, FileTextIcon, LayoutDashboardIcon, LogOutIcon,
  MenuIcon, SearchIcon, Settings2Icon, ShieldCheckIcon,
  UserRoundIcon, UsersIcon, XIcon,
} from "lucide-react"
import { useAuth, useCurrentUser } from "@/lib/auth"
import { initials } from "@/lib/format"

const NAV = [
  { to: "/", label: "Visão geral", icon: LayoutDashboardIcon },
  { to: "/vagas", label: "Explorar vagas", icon: BriefcaseBusinessIcon },
  { to: "/candidaturas", label: "Minhas candidaturas", icon: FileTextIcon },
  { to: "/perfil", label: "Meu perfil", icon: UserRoundIcon },
]

const ADMIN_NAV = [
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

export function AppLayout() {
  const user = useCurrentUser()
  const { logout } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const sidebarRef = useRef<HTMLElement>(null)

  useEffect(() => {
    setMobileOpen(false)
    setProfileOpen(false)
  }, [pathname])

  useLayoutEffect(() => {
    if (!mobileOpen || window.innerWidth > 740) return

    // No Safari do iPhone, overflow:hidden sozinho nao impede a pagina
    // de rolar por tras de um drawer com position:fixed.
    const scrollY = window.scrollY
    const body = document.body
    const html = document.documentElement
    const oldBody = {
      position: body.style.position,
      top: body.style.top,
      left: body.style.left,
      right: body.style.right,
      width: body.style.width,
      overflow: body.style.overflow,
      overscrollBehavior: body.style.overscrollBehavior,
    }
    const oldHtmlOverflow = html.style.overflow
    const oldHtmlOverscroll = html.style.overscrollBehavior

    html.style.overflow = "hidden"
    html.style.overscrollBehavior = "none"
    body.style.position = "fixed"
    body.style.top = `-${scrollY}px`
    body.style.left = "0"
    body.style.right = "0"
    body.style.width = "100%"
    body.style.overflow = "hidden"
    body.style.overscrollBehavior = "none"

    // Focar o X sem deslocar o documento (causa frequente do salto no iOS).
    sidebarRef.current?.querySelector<HTMLButtonElement>(".rh-close-mobile")?.focus({ preventScroll: true })

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMobileOpen(false)
    }
    const onResize = () => {
      if (window.innerWidth > 740) setMobileOpen(false)
    }

    window.addEventListener("keydown", onKeyDown)
    window.addEventListener("resize", onResize)
    return () => {
      Object.assign(body.style, oldBody)
      html.style.overflow = oldHtmlOverflow
      html.style.overscrollBehavior = oldHtmlOverscroll
      window.removeEventListener("keydown", onKeyDown)
      window.removeEventListener("resize", onResize)
      window.scrollTo(0, scrollY)
    }
  }, [mobileOpen])

  async function handleLogout() {
    setProfileOpen(false)
    await logout()
    navigate("/login")
  }

  function navGroup(label: string, links: typeof NAV) {
    return (
      <div className="rh-nav-group">
        <p className="rh-nav-label">{label}</p>
        {links.map(({ to, label: name, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) => `rh-nav-item ${isActive ? "is-active" : ""}`}
          >
            <Icon className="size-[18px] shrink-0" />
            <span className="rh-nav-text">{name}</span>
            {to === "/vagas" && <span className="rh-nav-item-arrow"><ArrowUpRightIcon className="size-3.5" /></span>}
          </NavLink>
        ))}
      </div>
    )
  }

  return (
    <div className="rh-shell">
      {mobileOpen && <button type="button" className="rh-mobile-shade" onClick={() => setMobileOpen(false)} aria-label="Fechar menu" />}
      <aside
        ref={sidebarRef}
        id="rhhub-mobile-navigation"
        className={`rh-sidebar ${mobileOpen ? "is-open" : ""}`}
        role={mobileOpen ? "dialog" : undefined}
        aria-modal={mobileOpen ? "true" : undefined}
        aria-label="Menu de navegação"
      >
        <div className="rh-brand">
          <NavLink to="/" className="rh-brand-link" onClick={() => setMobileOpen(false)}>
            <span className="rh-brand-mark"><BriefcaseBusinessIcon className="size-[21px]" strokeWidth={2.1} /></span>
            <span className="rh-brand-name">rh<span>hub</span><span className="rh-brand-period">.</span></span>
          </NavLink>
          <button type="button" className="rh-icon-button rh-close-mobile" onClick={() => setMobileOpen(false)} aria-label="Fechar menu"><XIcon className="size-5" /></button>
        </div>

        <div className="rh-workspace">
          <span className="rh-workspace-symbol"><BriefcaseBusinessIcon className="size-[18px]" /></span>
          <div className="min-w-0 flex-1">
            <span className="rh-workspace-caption">SEU WORKSPACE</span>
            <span className="rh-workspace-name">Recrutamento</span>
          </div>
          <ChevronDownIcon className="size-4 opacity-50" />
        </div>

        <nav className="rh-sidebar-nav" aria-label="Navegação principal">
          {navGroup("ESPAÇO DE TRABALHO", NAV)}
          {user.role === "admin" && navGroup("ADMINISTRAÇÃO", ADMIN_NAV)}
        </nav>

        <div className="rh-sidebar-bottom">
          <div className="rh-side-help">
            <span className="rh-side-help-icon"><CircleHelpIcon className="size-5" /></span>
            <strong>Precisa de ajuda?</strong>
            <p>Explore os recursos e acompanhe suas oportunidades por aqui.</p>
            <NavLink to="/perfil" onClick={() => setMobileOpen(false)}>Ver meu perfil <ArrowUpRightIcon className="size-3.5" /></NavLink>
          </div>
          <div className="rh-side-user">
            <span className="rh-user-avatar">{initials(user.name)}</span>
            <div className="min-w-0 flex-1">
              <div className="truncate font-semibold text-[13px]">{user.name}</div>
              <div className="truncate text-[11px] text-[#8793ae]">{user.role === "admin" ? "Administrador" : "Candidato"}</div>
            </div>
            <button type="button" onClick={handleLogout} className="rh-sidebar-logout" title="Sair" aria-label="Sair da conta"><LogOutIcon className="size-[17px]" /></button>
          </div>
        </div>
      </aside>

      <div className="rh-main">
        <header className="rh-topbar">
          <div className="flex min-w-0 items-center gap-3">
            <button type="button" className="rh-topbar-mobile" onClick={() => setMobileOpen(true)} aria-label="Abrir menu" aria-controls="rhhub-mobile-navigation" aria-expanded={mobileOpen}><MenuIcon className="size-5" /></button>
            <div className="rh-breadcrumb"><span>Workspace</span><span className="rh-breadcrumb-divider">/</span><strong>{TITLES[pathname] ?? "RhHub"}</strong></div>
          </div>
          <div className="rh-top-actions">
            <NavLink to="/vagas" className="rh-top-search"><SearchIcon className="size-4" /><span>Buscar oportunidades</span><kbd>⌕</kbd></NavLink>
            <span className="rh-demo-tag">AMBIENTE DEMO</span>
            <button type="button" className="rh-top-icon" title="Notificações (demonstração)" aria-label="Notificações"><BellIcon className="size-[18px]" /><i /></button>
            <div className="rh-topbar-profile">
              <button type="button" className="rh-top-profile-btn" onClick={() => setProfileOpen((v) => !v)} aria-label="Opções da conta" aria-expanded={profileOpen}>
                <span className="rh-top-avatar">{initials(user.name)}</span>
                <ChevronDownIcon className="size-3.5" />
              </button>
              {profileOpen && (
                <div className="rh-profile-menu">
                  <p className="truncate px-3 pt-2 pb-2 text-xs text-muted-foreground">{user.email}</p>
                  <button onClick={() => {setProfileOpen(false); navigate("/perfil")}}><Settings2Icon className="size-4" /> Configurações</button>
                  <button onClick={handleLogout}><LogOutIcon className="size-4" /> Sair</button>
                </div>
              )}
            </div>
          </div>
        </header>
        <main className="rh-page" id="conteudo"><Outlet /></main>
        <footer className="rh-footer"><span>RhHub · Plataforma de recrutamento</span><span>Protótipo de interface · Dados de demonstração</span></footer>
      </div>
    </div>
  )
}
