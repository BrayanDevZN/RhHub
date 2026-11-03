import { useState } from "react"
import { LockIcon, PlusIcon, RotateCcwIcon, XIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { DEFAULT_STAGES, MAX_STAGES, SCREENING_STAGE } from "@/lib/types"

/** Etapas da vaga em ordem. A Triagem é fixa (obrigatória); as demais podem sair, ser trocadas ou acrescentadas. */
export function StageEditor({ stages, onChange }: { stages: string[]; onChange: (stages: string[]) => void }) {
  const [draft, setDraft] = useState("")
  const extras = stages.filter((s) => s !== SCREENING_STAGE)

  function add() {
    const name = draft.trim().replace(/\s+/g, " ")
    if (!name) return
    if (!stages.some((s) => s.toLowerCase() === name.toLowerCase()) && stages.length < MAX_STAGES) {
      onChange([...stages, name])
    }
    setDraft("")
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="secondary">
          <LockIcon data-icon="inline-start" />
          {SCREENING_STAGE}
        </Badge>
        {extras.map((stage) => (
          <Badge key={stage} variant="outline" className="gap-1 pr-1">
            {stage}
            <button
              type="button"
              className="rounded-sm p-0.5 hover:bg-muted"
              aria-label={`Remover etapa ${stage}`}
              onClick={() => onChange(stages.filter((s) => s !== stage))}
            >
              <XIcon className="size-3" />
            </button>
          </Badge>
        ))}
      </div>
      <div className="flex gap-2">
        <Input
          value={draft}
          maxLength={40}
          placeholder="Nova etapa (ex.: Teste técnico)"
          disabled={stages.length >= MAX_STAGES}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault()
              add()
            }
          }}
        />
        <Button type="button" variant="outline" onClick={add} disabled={!draft.trim() || stages.length >= MAX_STAGES}>
          <PlusIcon data-icon="inline-start" />
          Adicionar
        </Button>
        <Button type="button" variant="ghost" size="icon" aria-label="Restaurar etapas padrão" onClick={() => onChange(DEFAULT_STAGES)}>
          <RotateCcwIcon />
        </Button>
      </div>
    </div>
  )
}
