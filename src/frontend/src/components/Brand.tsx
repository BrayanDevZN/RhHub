import { BriefcaseBusinessIcon } from "lucide-react"
import { cn } from "@/lib/utils"

/** Marca do RhHub: ícone com gradiente + nome. Com `compact` mostra só o ícone (sidebar minimizada). */
export function Brand({ className, compact = false }: { className?: string; compact?: boolean }) {
  return (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-indigo-400 via-violet-400 to-cyan-400 text-white shadow-lg shadow-indigo-500/25">
        <BriefcaseBusinessIcon className="size-5" strokeWidth={2.1} />
      </span>
      {!compact && (
        <span className="text-2xl font-extrabold tracking-tighter text-foreground">
          <span className="font-normal">rh</span>hub<span className="text-cyan-400">.</span>
        </span>
      )}
    </span>
  )
}
