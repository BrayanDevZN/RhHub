import { CheckIcon } from "lucide-react"
import { cn } from "cn"
import type { Resume } from "@/lib/types"

/** Mostra por onde o candidato já passou, em que etapa está e o que falta. Recusado na triagem não avança. */
export function StageProgress({ stages, resume }: { stages: string[]; resume: Pick<Resume, "stage" | "status"> }) {
  const current = Math.max(
    0,
    stages.findIndex((s) => s.toLowerCase() === resume.stage.toLowerCase()),
  )
  const refused = resume.status === "recuse"
  return (
    <ol className="flex flex-col items-start gap-y-1.5 text-xs sm:flex-row sm:flex-wrap sm:items-center sm:gap-x-2 sm:gap-y-1">
      {stages.map((stage, index) => {
        const done = index < current || (index === current && !refused && resume.status === "aproved" && index > 0)
        const active = index === current
        return (
          <li key={stage} className="flex items-center gap-2">
            <span
              className={cn(
                "flex items-center gap-1 rounded-full border px-2 py-0.5",
                active && !refused && "border-primary bg-primary text-primary-foreground",
                active && refused && "border-destructive text-destructive",
                !active && done && "text-foreground",
                !active && !done && "text-muted-foreground",
              )}
            >
              {done && !active && <CheckIcon className="size-3" />}
              {stage}
            </span>
            {index < stages.length - 1 && <span className="hidden text-muted-foreground sm:inline">›</span>}
          </li>
        )
      })}
    </ol>
  )
}
