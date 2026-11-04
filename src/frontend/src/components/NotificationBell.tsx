import { useCallback, useEffect, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import { BellIcon, CheckCheckIcon } from "lucide-react"
import { api } from "@/lib/api"
import { timeAgo } from "@/lib/format"
import type { AppNotification, Inbox } from "@/lib/types"

const POLL_MS = 30_000

/** O sino da barra: mostra quantos avisos não foram lidos, abre a lista e leva ao assunto de cada aviso. */
export function NotificationBell() {
  const navigate = useNavigate()
  const [inbox, setInbox] = useState<Inbox>({ items: [], unread: 0 })
  const [open, setOpen] = useState(false)
  const [failed, setFailed] = useState(false)
  const box = useRef<HTMLDivElement>(null)

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

  useEffect(() => {
    if (!open) return
    function onDown(event: MouseEvent) {
      if (box.current && !box.current.contains(event.target as Node)) setOpen(false)
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false)
    }
    document.addEventListener("mousedown", onDown)
    document.addEventListener("keydown", onKey)
    return () => {
      document.removeEventListener("mousedown", onDown)
      document.removeEventListener("keydown", onKey)
    }
  }, [open])

  function toggle() {
    const next = !open
    setOpen(next)
    if (next) void load()
  }

  async function openItem(item: AppNotification) {
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
    <div className="rh-notif" ref={box}>
      <button className="rh-top-icon" title="Notificações" aria-label={label} aria-expanded={open} onClick={toggle}>
        <BellIcon className="size-[18px]" />
        {inbox.unread > 0 && <b className="rh-notif-count">{inbox.unread > 9 ? "9+" : inbox.unread}</b>}
      </button>
      {open && (
        <div className="rh-notif-panel" role="dialog" aria-label="Notificações">
          <div className="rh-notif-head">
            <strong>Notificações</strong>
            <button type="button" onClick={readAll} disabled={inbox.unread === 0}>
              <CheckCheckIcon className="size-3.5" /> Marcar todas como lidas
            </button>
          </div>
          {inbox.items.length === 0 ? (
            <p className="rh-notif-empty">{failed ? "Não foi possível carregar agora." : "Você não tem notificações."}</p>
          ) : (
            <ul>
              {inbox.items.map((item) => (
                <li key={item.id}>
                  <button type="button" className={item.read ? "" : "rh-notif-unread"} onClick={() => openItem(item)}>
                    <span className="rh-notif-dot" aria-hidden="true" />
                    <span className="rh-notif-body">
                      <strong>{item.title}</strong>
                      <span>{item.message}</span>
                      <small>{timeAgo(item.created_at)}</small>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
