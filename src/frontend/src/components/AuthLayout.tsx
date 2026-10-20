import { lazy, Suspense, type ReactNode } from "react"
import { BriefcaseBusinessIcon } from "lucide-react"

const Scene3D = lazy(() => import("@/components/Scene3D"))

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative min-h-svh overflow-hidden bg-background">
      <Suspense fallback={null}>
        <Scene3D className="absolute inset-0 opacity-90" />
      </Suspense>
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-br from-background/70 via-background/20 to-background/80" />

      <div className="relative mx-auto grid min-h-svh w-full max-w-6xl items-center gap-10 px-6 py-10 lg:grid-cols-2">
        <div className="hidden flex-col gap-6 lg:flex">
          <div className="flex items-center gap-2 text-lg font-semibold">
            <span className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <BriefcaseBusinessIcon className="size-5" />
            </span>
            RhHub
          </div>
          <h1 className="text-4xl font-semibold tracking-tight">
            Recrutamento com análise de currículos feita por IA.
          </h1>
          <p className="max-w-md text-muted-foreground">
            Publique vagas, receba candidaturas e acompanhe o resultado da triagem automática por e-mail.
          </p>
        </div>

        <div className="flex justify-center lg:justify-end">{children}</div>
      </div>
    </div>
  )
}
