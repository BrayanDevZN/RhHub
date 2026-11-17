import { useCallback, useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { BellIcon, CheckCheckIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { api } from "@/lib/api"
import { timeAgo } from "@/lib/format"
import { cn } from "@/lib/utils"
import type { AppNotification, Inbox } from "@/lib/types"

const POLL_MS = 30_000

/** O sino da barra: mostra quantos avisos não foram lidos, abre a lista e leva ao assunto de cada aviso. */
export function NotificationBell() {
  const navigate = useNavigate()
  const [inbox, setInbox] = useState<Inbox>({ items: [], unread: 0 })
  const [open, setOpen] = useState(false)
  const [failed, setFailed] = useState(false)

  const load = useCallback(async () => {
    try {
      setInbox(await api.notifications.inbox())
      setFailed(false)
    } catch {
      setFailed(true) // sem rede ou sessão acabando: tenta de novo no próximo ciclo, sem barulho
    }
  }, [])

  // Busca ao entrar, de tempos em tempos e quando a aba volta ao foco.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
    const timer = setInterval(load, POLL_MS)
    const onFocus = () => void load()
    window.addEventListener("focus", onFocus)
    return () => {
      clearInterval(timer)
      window.removeEventListener("focus", onFocus)
    }
  }, [load])

  function onOpenChange(next: boolean) {
    setOpen(next)
    if (next) void load()
  }

  function openItem(item: AppNotification) {
    setOpen(false)
    if (!item.read) {
      setInbox((current) => ({
        items: current.items.map((n) => (n.id === item.id ? { ...n, read: true } : n)),
        unread: Math.max(0, current.unread - 1),
      }))
      api.notifications.markRead(item.id).catch(() => void load())
    }
    if (item.link) navigate(item.link)
  }

  async function readAll() {
    setInbox((current) => ({ items: current.items.map((n) => ({ ...n, read: true })), unread: 0 }))
    try {
      await api.notifications.markAllRead()
    } catch {
      void load()
    }
  }

  const label = inbox.unread > 0 ? `Notificações (${inbox.unread} não lidas)` : "Notificações"

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger
        render={<Button variant="ghost" size="icon" className="relative" title="Notificações" aria-label={label} />}
      >
        <BellIcon />
        {inbox.unread > 0 && (
          <Badge className="absolute -top-0.5 -right-0.5 h-4 min-w-4 justify-center rounded-full px-1 text-[10px] leading-none">
            {inbox.unread > 9 ? "9+" : inbox.unread}
          </Badge>
        )}
      </PopoverTrigger>
      <PopoverContent align="end" sideOffset={8} className="w-96 gap-0 p-0" aria-label="Notificações">
        <div className="flex items-center justify-between gap-2 border-b px-3 py-2.5">
          <strong className="text-sm font-semibold">Notificações</strong>
          <Button type="button" variant="ghost" size="xs" onClick={readAll} disabled={inbox.unread === 0}>
            <CheckCheckIcon data-icon="inline-start" /> Marcar todas como lidas
          </Button>
        </div>
        {inbox.items.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-muted-foreground">
            {failed ? "Não foi possível carregar agora." : "Você não tem notificações."}
          </p>
        ) : (
          <ul className="max-h-[min(380px,60vh)] overflow-y-auto p-1">
            {inbox.items.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  onClick={() => openItem(item)}
                  className="flex w-full items-start gap-3 rounded-md px-2.5 py-2 text-left outline-none transition-colors hover:bg-muted focus-visible:bg-muted"
                >
                  <span
                    aria-hidden="true"
                    className={cn("mt-1.5 size-2 shrink-0 rounded-full", item.read ? "bg-transparent" : "bg-primary")}
                  />
                  <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <strong className={cn("truncate text-sm", item.read ? "font-medium" : "font-semibold")}>{item.title}</strong>
                    <span className="line-clamp-2 text-xs text-muted-foreground">{item.message}</span>
                    <small className="text-[11px] text-muted-foreground/80">{timeAgo(item.created_at)}</small>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </PopoverContent>
    </Popover>
  )
}
