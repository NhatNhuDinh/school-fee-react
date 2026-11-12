import { useCustom } from "@refinedev/core";
import { useEffect, useState } from "react";

import type {
  BatchItemLine,
  FeeCollectionBatchPreview,
  FeeCollectionScope,
} from "@/domains/fee-collection-batch/types";
import { API_URL } from "@/shared/api/constants";
import { customResult } from "@/shared/hooks/use-custom-result";

export type PreviewInput = {
  yearId: number;
  month: number;
  scope: FeeCollectionScope;
  classGroupId: number | null;
  schoolClassId: number | null;
  lines: BatchItemLine[];
};

/** A value that follows `value` after it has stopped changing for a moment. */
function useDebounced<T>(value: T, delayMs: number): T {
  const [settled, setSettled] = useState(value);
  const key = JSON.stringify(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs);
    return () => clearTimeout(timer);
    // `key` stands for the value: an object rebuilt on every render must not restart the timer.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, delayMs]);
  return settled;
}

/** Whether the scope names what it needs: a khối lớp for "theo khối", a lớp for "theo lớp". */
function targetChosen(input: PreviewInput): boolean {
  if (input.scope === "CLASS_GROUP") return input.classGroupId !== null;
  if (input.scope === "CLASS") return input.schoolClassId !== null;
  return true;
}

/**
 * What a đợt thu would be, asked of the API as the choices change: who it reaches, which
 * khoản thu could go in it, and what the chosen ones come to. It is the same planning
 * the create call runs, so the figure on screen is the figure that gets written.
 *
 * The previous answer stays on screen while the next one is on its way
 * (`isPlaceholderData`), so the dialog does not blank between two keystrokes.
 */
export function useBatchPreview(input: PreviewInput, enabled = true) {
  const settled = useDebounced(input, 250);
  const ready = enabled && targetChosen(settled);

  const { result, query } = useCustom<FeeCollectionBatchPreview>({
    url: `${API_URL}/fee_collection_batches/preview`,
    method: "post",
    config: {
      // The provider sends a JSON string with no content type of its own, which the API
      // answers 415; the rest of the data layer sets it per call.
      headers: { "Content-Type": "application/json" },
      payload: {
        academicYear: { id: settled.yearId },
        collectionMonth: settled.month,
        scope: settled.scope,
        scopeClassGroup: settled.classGroupId === null ? null : { id: settled.classGroupId },
        scopeClass: settled.schoolClassId === null ? null : { id: settled.schoolClassId },
        items: settled.lines,
      },
    },
    queryOptions: {
      enabled: ready,
      placeholderData: (previous) => previous,
    },
    successNotification: false,
    errorNotification: false,
  });

  return {
    preview: customResult(result?.data, "candidates"),
    /** True while the answer on screen is for choices that have since changed. */
    pending: !ready || query.isFetching || JSON.stringify(settled) !== JSON.stringify(input),
    /** The answer shown belongs to an earlier question. */
    isPlaceholder: query.isPlaceholderData,
    ready,
  };
}
