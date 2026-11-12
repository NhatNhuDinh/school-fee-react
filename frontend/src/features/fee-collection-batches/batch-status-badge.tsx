import {
  BATCH_STATUS_LABELS,
  type FeeCollectionBatchStatus,
} from "@/domains/fee-collection-batch/types";
import { Badge } from "@/shared/ui/badge";

const VARIANT = {
  NOT_STARTED: "neutral",
  COLLECTING: "warning",
  COMPLETED: "success",
} as const;

/** The status of a đợt thu, which the API reads off the money — never chosen by a person. */
export function BatchStatusBadge({ status }: { status: FeeCollectionBatchStatus }) {
  return <Badge variant={VARIANT[status]}>{BATCH_STATUS_LABELS[status]}</Badge>;
}
