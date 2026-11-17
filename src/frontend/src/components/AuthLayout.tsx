import { lazy, Suspense, type ReactNode } from "react"
import { Brand } from "@/components/Brand"

const Scene3D = lazy(() => import("@/components/Scene3D"))

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="rh-auth relative isolate min-h-svh overflow-hidden bg-background text-white">
      <Suspense fallback={null}>
        <Scene3D className="rh-auth-scene pointer-events-none absolute inset-0 opacity-90" />
      </Suspense>
      <div className="rh-auth-scrim pointer-events-none absolute inset-0" />

      <div className="relative mx-auto grid min-h-svh w-full max-w-6xl items-center gap-10 px-6 py-10 lg:grid-cols-2">
        <div className="hidden flex-col gap-6 lg:flex">
          <Brand className="text-white" />
          <h1 className="text-4xl font-semibold tracking-tight text-white">
            Recrutamento com análise de currículos feita por IA.
          </h1>
          <p className="max-w-md text-white">
            Publique vagas, receba candidaturas e acompanhe o resultado da triagem automática por e-mail.
          </p>
        </div>

        <div className="flex justify-center lg:justify-end">{children}</div>
      </div>
    </div>
  )
}
