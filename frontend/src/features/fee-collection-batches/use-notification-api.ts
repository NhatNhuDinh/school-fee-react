import { useCustom } from "@refinedev/core";
import { useEffect, useState } from "react";

import type {
  NotificationCandidatePage,
  NotificationDefaults,
  NotificationTree,
  RenderedNotification,
  SelectionMode,
} from "@/domains/fee-notification/types";
import { API_URL } from "@/shared/api/constants";
import { kyInstance } from "@/shared/api/data";
import { customResult } from "@/shared/hooks/use-custom-result";

/** A value that follows `value` after it has stopped changing for a moment. */
export function useDebounced<T>(value: T, delayMs: number): T {
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

/** Which students the picker is showing: the node of the tree, the mode and the narrowing filters. */
export type CandidateView = {
  mode: SelectionMode;
  classGroupId: number | null;
  schoolClassId: number | null;
  collect: "any" | "unpaid" | "partial" | "paid";
  notify: "any" | "none" | "queued" | "sent" | "failed";
  q: string;
};

/** The query string for a view; "any" and empty values are left out, which restricts nothing. */
export function viewQuery(batchId: number, view: CandidateView, withPlace = true): Record<string, string | number> {
  const query: Record<string, string | number> = { batchId, mode: view.mode };
  if (withPlace && view.classGroupId !== null) query.classGroupId = view.classGroupId;
  if (withPlace && view.schoolClassId !== null) query.schoolClassId = view.schoolClassId;
  if (view.collect !== "any") query.collect = view.collect;
  if (view.notify !== "any") query.notify = view.notify;
  if (view.q.trim()) query.q = view.q.trim();
  return query;
}

/** One page of the students a notification could go to, with what the whole filtered set comes to. */
export function useCandidates(batchId: number, view: CandidateView, page: number, pageSize: number, tick = 0) {
  const { result, query } = useCustom<NotificationCandidatePage>({
    url: `${API_URL}/fee_notifications/candidates`,
    method: "get",
    config: {
      query: { ...viewQuery(batchId, view), _start: (page - 1) * pageSize, _end: page * pageSize, _v: tick },
    },
    queryOptions: { placeholderData: (previous) => previous },
    errorNotification: false,
  });
  return {
    data: customResult(result?.data, "rows"),
    isLoading: query.isLoading,
    isFetching: query.isFetching,
  };
}

/** Khối and lớp with their student counts under the current mode and filters (not the node chosen). */
export function useNotificationTree(batchId: number, view: CandidateView, tick = 0) {
  const { result, query } = useCustom<NotificationTree>({
    url: `${API_URL}/fee_notifications/tree`,
    method: "get",
    config: { query: { ...viewQuery(batchId, view, false), _v: tick } },
    queryOptions: { placeholderData: (previous) => previous },
    errorNotification: false,
  });
  return { tree: customResult(result?.data, "groups"), isLoading: query.isLoading };
}

export function useNotificationDefaults(batchId: number) {
  const { result } = useCustom<NotificationDefaults>({
    url: `${API_URL}/fee_notifications/defaults`,
    method: "get",
    config: { query: { batchId } },
    errorNotification: false,
  });
  return customResult(result?.data, "subject");
}

export type NotificationPreviewInput = {
  batchId: number;
  batchStudentId: number | null;
  subject: string;
  content: string;
  deadline: string;
};

/** The e-mail one student would get with these words, asked of the API as they change. */
export function useNotificationPreview(input: NotificationPreviewInput, enabled: boolean) {
  const settled = useDebounced(input, 300);
  const ready = enabled && settled.batchStudentId !== null && settled.deadline !== "";
  const { result, query } = useCustom<RenderedNotification>({
    url: `${API_URL}/fee_notifications/preview`,
    method: "post",
    config: {
      headers: { "Content-Type": "application/json" },
      payload: {
        batchId: settled.batchId,
        batchStudentId: settled.batchStudentId,
        subject: settled.subject,
        content: settled.content,
        deadline: settled.deadline,
      },
    },
    queryOptions: { enabled: ready, placeholderData: (previous) => previous },
    successNotification: false,
    errorNotification: false,
  });
  return {
    preview: customResult(result?.data, "html"),
    isFetching: query.isFetching,
    error: query.error as { message?: string } | null,
  };
}

export type CandidateIdAmount = { id: number; outstanding: number };

/** Every student a view reaches (and what each still owes), for "select all" — the page only holds some of them. */
export async function fetchCandidateIds(
  batchId: number,
  view: CandidateView,
  owingOnly: boolean
): Promise<CandidateIdAmount[]> {
  const searchParams: Record<string, string | number> = { ...viewQuery(batchId, view) };
  if (owingOnly) searchParams.owing = 1;
  return kyInstance.get("fee_notifications/candidate-ids", { searchParams }).json<CandidateIdAmount[]>();
}
