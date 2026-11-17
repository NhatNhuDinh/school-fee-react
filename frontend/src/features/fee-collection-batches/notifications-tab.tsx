import { useCan, useList } from "@refinedev/core";
import React from "react";

import type { FeeCollectionBatch } from "@/domains/fee-collection-batch/types";
import {
  NOTIFICATION_STATUS_LABELS,
  isNotificationBusy,
  type CreatedNotification,
  type FeeNotification,
  type FeeNotificationStatus,
} from "@/domains/fee-notification/types";
import { Search } from "lucide-react";
import { formatDateTime, formatNumber } from "@/shared/lib/format";
import { Input } from "@/shared/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/shared/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/shared/ui/table";
import { toast } from "sonner";
import { NotificationComposeDialog } from "./notification-compose-dialog";
import { NotificationDetailDialog } from "./notification-detail-dialog";
import { NotificationStatusBadge } from "./notification-status";
import { ProgressBar } from "./progress-bar";

/**
 * The Thông báo tab of a đợt thu: every notification made for it, with the totals above, and the
 * button that starts a new one. While any of them is still going out the list refreshes itself.
 */
export function NotificationsTab({
  batch,
  onChanged,
  composing,
  setComposing,
}: {
  batch: FeeCollectionBatch;
  onChanged: () => void;
  composing: boolean;
  setComposing: (open: boolean) => void;
}) {
  const [openId, setOpenId] = React.useState<number | null>(null);
  const [search, setSearch] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState<"any" | FeeNotificationStatus>("any");
  const { data: canWrite } = useCan({ resource: "fee_notifications", action: "create" });

  const { result, query } = useList<FeeNotification>({
    resource: "fee_notifications",
    filters: [{ field: "batch.id", operator: "eq", value: batch.id }],
    sorters: [{ field: "id", order: "desc" }],
    pagination: { currentPage: 1, pageSize: 100 },
    queryOptions: {
      refetchInterval: (q) => {
        const rows = (q.state.data?.data ?? []) as FeeNotification[];
        return rows.some((row) => isNotificationBusy(row.status)) ? 3000 : false;
      },
    },
  });
  const allRows = result?.data ?? [];
  const opened = allRows.find((row) => row.id === openId) ?? null;
  // The đợt has a few dozen notifications at most, all fetched above, so this narrows locally.
  const term = search.trim().toLowerCase();
  const rows = allRows.filter(
    (row) =>
      (statusFilter === "any" || row.status === statusFilter) &&
      (term === "" || row.subject.toLowerCase().includes(term))
  );

  function created({ notification, requested, skippedAlreadyNotified, skippedNoDebt }: CreatedNotification, draft: boolean) {
    setComposing(false);
    void query.refetch();
    onChanged();
    const left = skippedAlreadyNotified + skippedNoDebt;
    toast.success(draft ? "Đã lưu nháp" : "Đã lên lịch gửi", {
      description:
        `${formatNumber(notification.recipientCount)} người nhận` +
        (left > 0 ? `, bỏ qua ${left}/${requested} học sinh đã được thông báo hoặc không còn nợ` : ""),
    });
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex shrink-0 flex-wrap items-center gap-2 px-4 py-2.5">
        <div className="relative min-w-60 flex-1">
          <Search className="text-muted-foreground absolute start-3 top-1/2 size-4 -translate-y-1/2" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Tìm theo tiêu đề thông báo…"
            className="ps-9"
          />
        </div>
        <Select value={statusFilter} onValueChange={(value) => setStatusFilter(value as "any" | FeeNotificationStatus)}>
          <SelectTrigger className="w-44" aria-label="Trạng thái">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="any">Trạng thái: Tất cả</SelectItem>
            {(Object.keys(NOTIFICATION_STATUS_LABELS) as FeeNotificationStatus[]).map((status) => (
              <SelectItem key={status} value={status}>
                {NOTIFICATION_STATUS_LABELS[status]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        <Table>
          <TableHeader sticky>
            <TableRow>
              <TableHead className="w-14 ps-5">STT</TableHead>
              <TableHead>Tiêu đề</TableHead>
              <TableHead className="w-32">Trạng thái</TableHead>
              <TableHead className="w-28 text-end">Người nhận</TableHead>
              <TableHead className="w-24 text-end">Đã gửi</TableHead>
              <TableHead className="w-20 text-end">Lỗi</TableHead>
              <TableHead className="w-40">Người lập</TableHead>
              <TableHead className="w-40">Lập lúc</TableHead>
              <TableHead className="w-44 pe-5">Tiến độ gửi</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row, index) => {
              const percent = row.recipientCount > 0 ? Math.round((row.sentCount / row.recipientCount) * 100) : 0;
              return (
                <TableRow key={row.id} className="cursor-pointer" onClick={() => setOpenId(row.id)}>
                  <TableCell className="text-muted-foreground ps-5 tabular-nums">{index + 1}</TableCell>
                  <TableCell className="font-medium">{row.subject}</TableCell>
                  <TableCell>
                    <NotificationStatusBadge status={row.status} />
                  </TableCell>
                  <TableCell className="text-end tabular-nums">{formatNumber(row.recipientCount)}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatNumber(row.sentCount)}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatNumber(row.failedCount + row.missingEmailCount)}</TableCell>
                  <TableCell>{row.createdByName ?? "—"}</TableCell>
                  <TableCell className="text-muted-foreground">{formatDateTime(row.createdAt)}</TableCell>
                  <TableCell className="pe-5">
                    <div className="flex items-center gap-2">
                      <ProgressBar percent={percent} className="flex-1" />
                      <span className="text-muted-foreground w-9 text-end text-xs tabular-nums">{percent}%</span>
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
            {rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={9} className="text-muted-foreground py-12 text-center">
                  {query.isLoading
                    ? "Đang tải…"
                    : allRows.length > 0
                      ? "Không có thông báo nào khớp bộ lọc."
                      : "Chưa có đợt thông báo nào. Bấm “Lập đợt thông báo” để bắt đầu."}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {composing && (
        <NotificationComposeDialog batch={batch} onClose={() => setComposing(false)} onCreated={created} />
      )}
      {opened && (
        <NotificationDetailDialog
          notification={opened}
          canWrite={canWrite?.can === true}
          onClose={() => setOpenId(null)}
          onChanged={() => {
            void query.refetch();
            onChanged();
          }}
        />
      )}
    </div>
  );
}

