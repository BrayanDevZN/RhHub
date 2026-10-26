import { CircleCheckIcon, CircleXIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Spinner } from "@/components/ui/spinner"
import { STATUS_LABEL, type ResumeStatus } from "@/lib/types"

export function StatusBadge({ status }: { status: ResumeStatus }) {
  if (status === "aproved") {
    return (
      <Badge>
        <CircleCheckIcon data-icon="inline-start" />
        {STATUS_LABEL.aproved}
      </Badge>
    )
  }
  if (status === "recuse") {
    return (
      <Badge variant="destructive">
        <CircleXIcon data-icon="inline-start" />
        {STATUS_LABEL.recuse}
      </Badge>
    )
  }
  return (
    <Badge variant="secondary">
      <Spinner data-icon="inline-start" />
      {STATUS_LABEL.pending}
    </Badge>
  )
}
