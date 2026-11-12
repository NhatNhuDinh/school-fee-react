import { useCustom, useCustomMutation } from "@refinedev/core";
import React from "react";

import {
  isNotificationBusy,
  RECIPIENT_STATUS_LABELS,
  type FeeNotification,
  type NotificationRecipientPage,
} from "@/domains/fee-notification/types";
import { API_URL } from "@/shared/api/constants";
import { DataTablePagination } from "@/shared/components/data-table/data-table-pagination";
import { customResult } from "@/shared/hooks/use-custom-result";
import { formatDate, formatDateTime, formatNumber } from "@/shared/lib/format";
import { Button } from "@/shared/ui/button";
import { Checkbox } from "@/shared/ui/checkbox";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/shared/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/shared/ui/table";
import { NotificationStatusBadge, RecipientStatusLabel } from "./notification-status";
import { Segmented } from "./segmented";

type Filter = "ALL" | "SENT" | "FAILED" | "PENDING" | "MISSING_EMAIL";

/**
 * One đợt thông báo: who it went to and what became of each. Failed recipients can be ticked and
 * sent again; a draft can be sent from here; while it is sending the list refreshes itself.
 */
export function NotificationDetailDialog({
  notification,
  canWrite,
  onClose,
  onChanged,
}: {
  notification: FeeNotification;
  canWrite: boolean;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [filter, setFilter] = React.useState<Filter>("ALL");
  const [page, setPage] = React.useState(1);
  const [pageSize, setPageSize] = React.useState(50);
  const [picked, setPicked] = React.useState<Set<number>>(new Set());
  const busy = isNotificationBusy(notification.status);

  const { result, query } = useCustom<NotificationRecipientPage>({
    url: `${API_URL}/fee_notifications/${notification.id}/recipients`,
    method: "get",
    config: {
      query: {
        _start: (page - 1) * pageSize,
        _end: page * pageSize,
        ...(filter !== "ALL" ? { status: filter } : {}),
      },
    },
    queryOptions: { placeholderData: (previous) => previous, refetchInterval: busy ? 3000 : false },
    errorNotification: false,
  });
  const data = customResult(result?.data, "rows");
  const rows = data?.rows ?? [];
  const counts = data?.countsByStatus ?? {};

  const { mutate, mutation } = useCustomMutation();
  const act = (path: string, values: unknown) =>
    mutate(
      {
        url: `${API_URL}/fee_notifications/${notification.id}/${path}`,
        method: "post",
        config: { headers: { "Content-Type": "application/json" } },
        values: values as Record<string, unknown>,
        successNotification: { type: "success", message: path === "send" ? "Đã lên lịch gửi" : "Đã đưa vào hàng gửi lại" },
      },
      {
        onSuccess: () => {
          setPicked(new Set());
          void query.refetch();
          onChanged();
        },
      }
    );

  const options: { value: Filter; label: string }[] = [
    { value: "ALL", label: "Tất cả" },
    { value: "SENT", label: `Đã gửi · ${counts.SENT ?? 0}` },
    { value: "FAILED", label: `Gửi lỗi · ${counts.FAILED ?? 0}` },
    { value: "MISSING_EMAIL", label: `Thiếu email · ${counts.MISSING_EMAIL ?? 0}` },
    { value: "PENDING", label: `Chờ gửi · ${counts.PENDING ?? 0}` },
  ];

  const retryable = rows.filter((row) => row.status === "FAILED");

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        aria-describedby={undefined}
        className="flex h-[min(720px,calc(100dvh-2rem))] max-w-none flex-col gap-0 overflow-hidden p-0 sm:max-w-[min(1000px,calc(100vw-2rem))]"
      >
        <DialogHeader className="shrink-0 gap-1 border-b px-6 py-4 pe-14">
          <div className="flex items-center gap-3">
            <DialogTitle className="truncate text-base">{notification.subject}</DialogTitle>
            <NotificationStatusBadge status={notification.status} />
          </div>
          <p className="text-muted-foreground text-xs">
            {formatNumber(notification.recipientCount)} người nhận · hạn đóng {formatDate(notification.deadline)}
            {notification.sendAt && ` · gửi lúc ${formatDateTime(notification.sendAt)}`}
          </p>
          {notification.errorMessage && <p className="text-destructive text-xs">{notification.errorMessage}</p>}
        </DialogHeader>

        <div className="shrink-0 px-6 py-3">
          <Segmented label="Lọc người nhận" size="sm" value={filter} options={options} onChange={(value) => { setFilter(value); setPage(1); }} />
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto">
          <Table>
            <TableHeader sticky>
              <TableRow>
                <TableHead className="w-10 ps-6">
                  {canWrite && retryable.length > 0 && (
                    <Checkbox
                      aria-label="Chọn các dòng gửi lỗi"
                      checked={retryable.every((row) => picked.has(row.id))}
                      onCheckedChange={(on) => setPicked(on === true ? new Set(retryable.map((row) => row.id)) : new Set())}
                    />
                  )}
                </TableHead>
                <TableHead className="w-12">STT</TableHead>
                <TableHead>Học sinh</TableHead>
                <TableHead className="w-24">Lớp</TableHead>
                <TableHead>Email phụ huynh</TableHead>
                <TableHead className="w-32 text-end">Số tiền</TableHead>
                <TableHead className="w-32">Trạng thái</TableHead>
                <TableHead className="w-40">Gửi lúc</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row, index) => (
                <TableRow key={row.id}>
                  <TableCell className="ps-6">
                    {canWrite && row.status === "FAILED" && (
                      <Checkbox
                        aria-label={`Chọn ${row.studentName}`}
                        checked={picked.has(row.id)}
                        onCheckedChange={(on) =>
                          setPicked((previous) => {
                            const next = new Set(previous);
                            if (on === true) next.add(row.id);
                            else next.delete(row.id);
                            return next;
                          })
                        }
                      />
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground tabular-nums">{(page - 1) * pageSize + index + 1}</TableCell>
                  <TableCell>
                    <div className="flex flex-col leading-tight">
                      <span className="font-medium">{row.studentName}</span>
                      <span className="text-muted-foreground text-xs">{row.studentCode}</span>
                    </div>
                  </TableCell>
                  <TableCell>{row.className}</TableCell>
                  <TableCell>
                    <div className="flex flex-col leading-tight">
                      <span>{row.email ?? "—"}</span>
                      {row.errorMessage && <span className="text-destructive text-xs">{row.errorMessage}</span>}
                    </div>
                  </TableCell>
                  <TableCell className="text-end tabular-nums">{formatNumber(row.totalAmount)}</TableCell>
                  <TableCell title={RECIPIENT_STATUS_LABELS[row.status]}>
                    <RecipientStatusLabel status={row.status} />
                  </TableCell>
                  <TableCell className="text-muted-foreground">{row.sentAt ? formatDateTime(row.sentAt) : "—"}</TableCell>
                </TableRow>
              ))}
              {rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} className="text-muted-foreground py-10 text-center">
                    {query.isLoading ? "Đang tải…" : "Không có người nhận nào"}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>

        <div className="shrink-0 border-t px-3 py-2">
          <DataTablePagination
            currentPage={page}
            pageCount={Math.max(1, Math.ceil((data?.total ?? 0) / pageSize))}
            setCurrentPage={setPage}
            pageSize={pageSize}
            setPageSize={(size) => {
              setPageSize(size);
              setPage(1);
            }}
            total={data?.total ?? 0}
          />
        </div>

        <DialogFooter className="h-16 shrink-0 flex-row items-center justify-end gap-2 border-t px-6">
          <Button type="button" variant="outline" onClick={onClose}>
            Đóng
          </Button>
          {canWrite && notification.status === "DRAFT" && (
            <Button type="button" disabled={mutation.isPending} onClick={() => act("send", {})}>
              Gửi ngay
            </Button>
          )}
          {canWrite && (notification.status === "PARTIAL_FAILURE" || notification.status === "FAILED") && (
            <Button
              type="button"
              disabled={mutation.isPending}
              onClick={() => act("retry", { recipientIds: picked.size > 0 ? [...picked] : null })}
            >
              {picked.size > 0 ? `Gửi lại ${picked.size} dòng đã chọn` : "Gửi lại các dòng lỗi"}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
