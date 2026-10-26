import { useCallback, useEffect, useState } from "react"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api"

// Busca dados e expõe reload; com pollMs, recarrega em intervalo enquanto shouldPoll for verdadeiro.
export function useQuery<T>(fetcher: () => Promise<T>, options: { pollMs?: number; shouldPoll?: (data: T) => boolean } = {}) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const { pollMs, shouldPoll } = options

  const reload = useCallback(async () => {
    try {
      setData(await fetcher())
    } catch (error) {
      toast.add({ title: "Erro", description: errorMessage(error), type: "error" })
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  const polling = pollMs !== undefined && data !== null && (shouldPoll ? shouldPoll(data) : true)
  useEffect(() => {
    if (!polling) return
    const timer = setInterval(reload, pollMs)
    return () => clearInterval(timer)
  }, [polling, pollMs, reload])

  return { data, loading, reload }
}
