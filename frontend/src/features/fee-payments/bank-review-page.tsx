import { useCan } from "@refinedev/core";
import React from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";

import {
  REASON_LABEL,
  type BankRow,
  type StudentDebts,
  type StudentHit,
  type TransferRequest,
} from "@/domains/fee-payment/types";
import { FEE_PAYMENTS, onConnected, subscribe } from "@/shared/api/live";
import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
import { NoAcademicYear } from "@/shared/components/academic-year/no-academic-year";
import { ListView, ListViewHeader } from "@/shared/components/views/list-view";
import { formatDateTime, formatNumber } from "@/shared/lib/format";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Checkbox } from "@/shared/ui/checkbox";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/shared/ui/dialog";
import { Textarea } from "@/shared/ui/textarea";
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from "@/shared/ui/table";
import {
  assignBankRow,
  cancelTransfer,
  checkBank,
  checkTransfer,
  confirmTransfer,
  describeCheck,
  errorMessage,
  fetchBankRows,
  fetchDebts,
  fetchPaymentSummary,
  ignoreBankRow,
  waitingTransfers,
} from "./api";
import { ConfirmDialog } from "./confirm-dialog";
import { StudentSearch } from "./student-search";

type Tab = "review" | "waiting" | "processed";

/**
 * Rà soát giao dịch: what the bank reported that no person has looked at yet, what is still waiting for
 * money, and what has been settled. The bank's report settles a matching transfer by itself; this screen
 * is for the rest — a line that matched nothing, a short payment, a webhook that never came.
 */
export const BankReviewPage = () => {
  const { enabled, selected: year, isLoading } = useAcademicYear();
  if (!enabled) {
    return (
      <ListView>
        <ListViewHeader title="Rà soát giao dịch" canCreate={false} />
        <Card className="text-muted-foreground p-10 text-center text-sm">
          Thu tiền thuộc một năm học của một trường. Hãy đăng nhập bằng tài khoản của trường để xem.
        </Card>
      </ListView>
    );
  }
  if (!isLoading && !year) return <NoAcademicYear />;
  if (!year) return null;
  return <ReviewOfYear yearId={year.id} />;
};

function ReviewOfYear({ yearId }: { yearId: number }) {
  const navigate = useNavigate();
  const [tab, setTab] = React.useState<Tab>("review");
  const [reload, setReload] = React.useState(0);
  const [rows, setRows] = React.useState<BankRow[]>([]);
  const [waiting, setWaiting] = React.useState<TransferRequest[]>([]);
  const [counts, setCounts] = React.useState({ review: 0, waiting: 0 });
  const [checking, setChecking] = React.useState(false);
  const [assigning, setAssigning] = React.useState<BankRow | null>(null);
  const [ignoring, setIgnoring] = React.useState<BankRow | null>(null);
  const [receiving, setReceiving] = React.useState<TransferRequest | null>(null);
  const [cancelling, setCancelling] = React.useState<TransferRequest | null>(null);
  const [busy, setBusy] = React.useState(false);
  const { data: canWrite } = useCan({ resource: "fee_payments", action: "edit" });

  const refresh = React.useCallback(() => setReload((n) => n + 1), []);

  React.useEffect(() => {
    let live = true;
    fetchPaymentSummary(yearId)
      .then((summary) => live && setCounts(summary))
      .catch(() => undefined);
    const load = tab === "waiting" ? waitingTransfers(yearId).then((list) => live && setWaiting(list))
      : fetchBankRows(yearId, tab).then((list) => live && setRows(list));
    load.catch((error) => live && toast.error(errorMessage(error)));
    return () => {
      live = false;
    };
  }, [yearId, tab, reload]);

  // The server pushes when a bank line arrives, is settled or is set aside: reload the list then.
  React.useEffect(() => {
    const stop = subscribe(FEE_PAYMENTS, (message) => {
      if ((message as { type?: string } | null)?.type === "review-changed") refresh();
    });
    const stopCatchUp = onConnected(refresh);
    return () => {
      stop();
      stopCatchUp();
    };
  }, [refresh]);

  async function checkAll() {
    setChecking(true);
    try {
      const summary = await checkBank(yearId);
      toast.success("Đã kiểm tra giao dịch", { description: describeCheck(summary) });
      refresh();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setChecking(false);
    }
  }

  async function checkOne(request: TransferRequest) {
    try {
      const result = await checkTransfer(yearId, request.id);
      if (result.request.status === "PAID") {
        toast.success("Đã nhận tiền", { description: `${request.studentName} · ${formatNumber(request.amount)} đ` });
      } else {
        toast.info("Chưa thấy khoản chuyển cho mã này", { description: describeCheck(result.summary) });
      }
      refresh();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  async function receiveByHand() {
    if (!receiving) return;
    setBusy(true);
    try {
      await confirmTransfer(yearId, receiving.id);
      toast.success("Đã ghi nhận tiền chuyển khoản");
      setReceiving(null);
      refresh();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  async function cancelRequest() {
    if (!cancelling) return;
    setBusy(true);
    try {
      await cancelTransfer(yearId, cancelling.id);
      toast.success("Đã hủy yêu cầu chuyển khoản");
      setCancelling(null);
      refresh();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  const tabs: { key: Tab; label: string; count?: number }[] = [
    { key: "review", label: "Cần rà soát", count: counts.review },
    { key: "waiting", label: "Chờ thanh toán", count: counts.waiting },
    { key: "processed", label: "Đã xử lý" },
  ];
  const total = tab === "waiting" ? waiting.reduce((sum, r) => sum + r.amount, 0) : rows.reduce((sum, r) => sum + r.amount, 0);
  const count = tab === "waiting" ? waiting.length : rows.length;

  return (
    <ListView className="h-[calc(100svh-4rem-2*var(--shell-gutter-y))] min-h-[36rem]">
      <ListViewHeader title="Rà soát giao dịch" canCreate={false} />
      <Card className="flex min-h-0 flex-1 flex-col gap-0 overflow-hidden p-0">
        <div className="flex flex-wrap items-center gap-3 border-b p-3">
          <div className="bg-muted flex gap-0.5 rounded-lg p-[3px]" role="tablist">
            {tabs.map((item) => (
              <button
                key={item.key}
                type="button"
                role="tab"
                aria-selected={tab === item.key}
                onClick={() => setTab(item.key)}
                className={cn(
                  "flex items-center gap-1.5 rounded-md px-3.5 py-1.5 text-sm whitespace-nowrap",
                  tab === item.key ? "bg-card font-semibold shadow-sm" : "text-muted-foreground hover:text-foreground"
                )}
              >
                {item.label}
                {item.count !== undefined ? (
                  <span className={cn("tabular-nums", tab === item.key && item.count > 0 && "text-primary font-bold")}>
                    {item.count}
                  </span>
                ) : null}
              </button>
            ))}
          </div>
          <Button
            type="button"
            variant="outline"
            className="border-primary text-primary hover:text-primary ms-auto"
            disabled={checking || !canWrite?.can}
            onClick={checkAll}
          >
            {checking ? "Đang kiểm tra…" : "Kiểm tra giao dịch"}
          </Button>
        </div>

        <div className="flex min-h-0 flex-1 flex-col">
          <Table containerClassName="min-h-0 flex-1 overflow-auto">
            <TableHeader className="sticky top-0 z-10">
              {tab === "waiting" ? (
                <TableRow>
                  <TableHead className="w-16">STT</TableHead>
                  <TableHead className="w-40">Tạo lúc</TableHead>
                  <TableHead className="w-48">Mã chuyển khoản</TableHead>
                  <TableHead>Học sinh</TableHead>
                  <TableHead className="w-24">Lớp</TableHead>
                  <TableHead className="w-40">Trạng thái</TableHead>
                  <TableHead className="w-36 text-right">Số tiền</TableHead>
                  <TableHead className="w-60">Thao tác</TableHead>
                </TableRow>
              ) : (
                <TableRow>
                  <TableHead className="w-14">STT</TableHead>
                  <TableHead className="w-40">Thời gian</TableHead>
                  <TableHead className="w-44">Nội dung CK</TableHead>
                  <TableHead>Gợi ý học sinh</TableHead>
                  <TableHead className="w-20">Lớp</TableHead>
                  <TableHead className="w-56">{tab === "review" ? "Lý do" : "Kết quả"}</TableHead>
                  <TableHead className="w-28">Mã GD SePay</TableHead>
                  <TableHead className="w-28 text-right">Số tiền</TableHead>
                  <TableHead className="w-44">{tab === "review" ? "Thao tác" : "Người xử lý"}</TableHead>
                </TableRow>
              )}
            </TableHeader>
            <TableBody>
              {count === 0 ? (
                <TableRow>
                  <TableCell colSpan={9} className="text-muted-foreground py-10 text-center">
                    {tab === "review"
                      ? "Không có giao dịch nào cần rà soát."
                      : tab === "waiting"
                        ? "Không có yêu cầu chuyển khoản nào đang chờ tiền về."
                        : "Chưa có giao dịch nào được xử lý."}
                  </TableCell>
                </TableRow>
              ) : tab === "waiting" ? (
                waiting.map((request, index) => (
                  <TableRow key={request.id}>
                    <TableCell className="text-muted-foreground">{index + 1}</TableCell>
                    <TableCell>{formatDateTime(request.createdAt)}</TableCell>
                    <TableCell className="font-mono text-xs">{request.orderCode}</TableCell>
                    <TableCell className="font-semibold">{request.studentName}</TableCell>
                    <TableCell>{request.className ?? "—"}</TableCell>
                    <TableCell>{request.status === "DEFERRED" ? "Rà soát sau" : "Đang chờ tiền về"}</TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{formatNumber(request.amount)}</TableCell>
                    <TableCell>
                      <span className="text-primary flex gap-3 text-sm font-semibold">
                        <button type="button" className="hover:underline" onClick={() => checkOne(request)}>
                          Kiểm tra
                        </button>
                        {canWrite?.can ? (
                          <>
                            <button type="button" className="hover:underline" onClick={() => setReceiving(request)}>
                              Đã nhận tiền
                            </button>
                            <button type="button" className="hover:underline" onClick={() => setCancelling(request)}>
                              Hủy
                            </button>
                          </>
                        ) : null}
                      </span>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                rows.map((row, index) => (
                  <TableRow key={row.id}>
                    <TableCell className="text-muted-foreground">{index + 1}</TableCell>
                    <TableCell>{formatDateTime(row.time)}</TableCell>
                    <TableCell className="max-w-44 truncate" title={row.content ?? ""}>
                      {row.content ?? "—"}
                    </TableCell>
                    <TableCell className="font-semibold">{row.suggestedStudentName ?? "—"}</TableCell>
                    <TableCell>{row.suggestedClassName ?? "—"}</TableCell>
                    <TableCell title={row.note ?? ""}>
                      {row.reason ? REASON_LABEL[row.reason] ?? row.reason : row.status === "MATCHED" ? "Đã khớp tự động" : "—"}
                      {row.note && tab === "processed" ? (
                        <span className="text-muted-foreground block truncate text-xs">{row.note}</span>
                      ) : null}
                    </TableCell>
                    <TableCell>{row.sepayId ? `#${row.sepayId}` : "—"}</TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{formatNumber(row.amount)}</TableCell>
                    <TableCell>
                      {tab === "review" ? (
                        <span className="text-primary flex gap-3 text-sm font-semibold">
                          {canWrite?.can ? (
                            <>
                              <button type="button" className="hover:underline" onClick={() => setAssigning(row)}>
                                Gán thủ công
                              </button>
                              <button type="button" className="hover:underline" onClick={() => setIgnoring(row)}>
                                Bỏ qua
                              </button>
                            </>
                          ) : null}
                        </span>
                      ) : (
                        (row.handledBy ?? (row.status === "MATCHED" ? "SePay (tự động)" : "—"))
                      )}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
            <TableFooter className="bg-muted sticky bottom-0 z-10">
              <TableRow className="hover:bg-muted">
                <TableCell colSpan={tab === "waiting" ? 6 : 7} className="font-semibold">
                  Tổng: {count} {tab === "waiting" ? "yêu cầu" : "giao dịch"}
                </TableCell>
                <TableCell className="text-right font-semibold tabular-nums">{formatNumber(total)}</TableCell>
                <TableCell />
              </TableRow>
            </TableFooter>
          </Table>
        </div>
      </Card>

      <div data-print="hide" className="flex shrink-0 justify-end">
        <Button type="button" variant="outline" onClick={() => navigate("/fee_payments")}>
          Quay lại
        </Button>
      </div>

      <AssignDialog
        yearId={yearId}
        row={assigning}
        onClose={() => setAssigning(null)}
        onDone={() => {
          setAssigning(null);
          refresh();
        }}
      />
      <IgnoreDialog
        yearId={yearId}
        row={ignoring}
        onClose={() => setIgnoring(null)}
        onDone={() => {
          setIgnoring(null);
          refresh();
        }}
      />
      <ConfirmDialog
        open={receiving !== null}
        title="Xác nhận đã nhận tiền?"
        description={`Ghi nhận ${formatNumber(receiving?.amount ?? 0)} đ chuyển khoản của ${receiving?.studentName ?? ""} khi chưa thấy giao dịch từ ngân hàng. Chỉ bấm khi bạn đã thấy tiền về tài khoản.`}
        confirmLabel="Đã nhận tiền"
        busy={busy}
        onConfirm={receiveByHand}
        onClose={() => setReceiving(null)}
      />
      <ConfirmDialog
        open={cancelling !== null}
        title="Hủy yêu cầu chuyển khoản?"
        description="Mã chuyển khoản này sẽ không còn dùng được. Nếu phụ huynh vẫn chuyển tiền vào mã này, giao dịch sẽ nằm ở mục Cần rà soát."
        confirmLabel="Hủy yêu cầu"
        destructive
        busy={busy}
        onConfirm={cancelRequest}
        onClose={() => setCancelling(null)}
      />
    </ListView>
  );
}

/** "Gán thủ công": point a bank line at the khoản thu it pays for; they must come to exactly its amount. */
function AssignDialog({
  yearId,
  row,
  onClose,
  onDone,
}: {
  yearId: number;
  row: BankRow | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const [enrollmentId, setEnrollmentId] = React.useState<number | null>(null);
  const [debts, setDebts] = React.useState<StudentDebts | null>(null);
  const [picked, setPicked] = React.useState<Set<number>>(new Set());
  const [busy, setBusy] = React.useState(false);

  React.useEffect(() => {
    setEnrollmentId(row?.suggestedEnrollmentId ?? null);
    setDebts(null);
    setPicked(new Set());
  }, [row]);

  React.useEffect(() => {
    if (!row || enrollmentId === null) return;
    let live = true;
    fetchDebts(yearId, enrollmentId)
      .then((result) => {
        if (!live) return;
        setDebts(result);
        // Start from the lines that add up to the amount, if some do: all of them, or all of one đợt.
        const open = result.batches.flatMap((b) => b.lines).filter((l) => l.outstanding > 0);
        const everything = open.reduce((s, l) => s + l.outstanding, 0);
        const oneBatch = result.batches.find((b) => b.outstanding === row.amount);
        setPicked(
          new Set(
            everything === row.amount
              ? open.map((l) => l.receivableId)
              : (oneBatch?.lines ?? []).filter((l) => l.outstanding > 0).map((l) => l.receivableId)
          )
        );
      })
      .catch((error) => live && toast.error(errorMessage(error)));
    return () => {
      live = false;
    };
  }, [row, enrollmentId, yearId]);

  const lines = (debts?.batches ?? []).flatMap((b) => b.lines.map((l) => ({ ...l, batch: b.batchName }))).filter((l) => l.outstanding > 0);
  const total = lines.filter((l) => picked.has(l.receivableId)).reduce((s, l) => s + l.outstanding, 0);
  const exact = row !== null && total === row.amount;

  async function submit() {
    if (!row || enrollmentId === null) return;
    setBusy(true);
    try {
      await assignBankRow(yearId, row.id, enrollmentId, [...picked]);
      toast.success("Đã gán giao dịch và ghi nhận thu tiền");
      onDone();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open={row !== null} onOpenChange={(open) => !open && !busy && onClose()}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Gán giao dịch cho khoản thu</DialogTitle>
        </DialogHeader>
        {row ? (
          <div className="grid gap-4">
            <p className="text-sm">
              Giao dịch <b>{formatNumber(row.amount)} đ</b> lúc {formatDateTime(row.time)}
              {row.content ? <span className="text-muted-foreground"> · “{row.content}”</span> : null}
            </p>
            <StudentSearch
              yearId={yearId}
              label={debts ? `${debts.fullName} (${debts.studentCode})${debts.className ? ` · ${debts.className}` : ""}` : null}
              onPick={(hit: StudentHit) => setEnrollmentId(hit.enrollmentId)}
              onClear={() => {
                setEnrollmentId(null);
                setDebts(null);
                setPicked(new Set());
              }}
            />
            <div className="max-h-64 overflow-y-auto rounded-md border">
              {lines.length === 0 ? (
                <p className="text-muted-foreground p-4 text-sm">Chọn học sinh để xem các khoản còn phải thu.</p>
              ) : (
                lines.map((line) => (
                  <label key={line.receivableId} className="hover:bg-accent/50 flex cursor-pointer items-center gap-3 border-b px-3 py-2 text-sm last:border-0">
                    <Checkbox
                      checked={picked.has(line.receivableId)}
                      onCheckedChange={(value) => {
                        const next = new Set(picked);
                        if (value === true) next.add(line.receivableId);
                        else next.delete(line.receivableId);
                        setPicked(next);
                      }}
                    />
                    <span className="min-w-0 flex-1 truncate">
                      {line.itemName}
                      <span className="text-muted-foreground"> · {line.batch}</span>
                    </span>
                    <span className="w-24 text-right font-semibold tabular-nums">{formatNumber(line.outstanding)}</span>
                  </label>
                ))
              )}
            </div>
            <p className={cn("text-sm", exact ? "text-success-text" : "text-destructive")}>
              Đã chọn {formatNumber(total)} đ / giao dịch {formatNumber(row.amount)} đ
              {exact ? " — khớp." : " — các khoản đã chọn phải đúng bằng số tiền giao dịch (hệ thống không thu một phần)."}
            </p>
          </div>
        ) : null}
        <DialogFooter>
          <Button type="button" variant="outline" disabled={busy} onClick={onClose}>
            Đóng
          </Button>
          <Button type="button" disabled={!exact || busy} onClick={submit}>
            {busy ? "Đang ghi nhận…" : "Gán và ghi nhận"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** "Bỏ qua": nothing to collect for this line; the note says why (a refund made, a transfer meant for something else). */
function IgnoreDialog({
  yearId,
  row,
  onClose,
  onDone,
}: {
  yearId: number;
  row: BankRow | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const [note, setNote] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  React.useEffect(() => setNote(""), [row]);

  async function submit() {
    if (!row) return;
    setBusy(true);
    try {
      await ignoreBankRow(yearId, row.id, note);
      toast.success("Đã bỏ qua giao dịch");
      onDone();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open={row !== null} onOpenChange={(open) => !open && !busy && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Bỏ qua giao dịch này?</DialogTitle>
        </DialogHeader>
        <p className="text-sm">
          {row ? `${formatNumber(row.amount)} đ lúc ${formatDateTime(row.time)}. ` : ""}
          Giao dịch được giữ lại ở mục Đã xử lý, không tạo phiếu thu.
        </p>
        <Textarea
          value={note}
          placeholder="Ghi chú (ví dụ: chuyển nhầm, đã hoàn tiền)"
          maxLength={500}
          onChange={(event) => setNote(event.target.value)}
        />
        <DialogFooter>
          <Button type="button" variant="outline" disabled={busy} onClick={onClose}>
            Không
          </Button>
          <Button type="button" disabled={busy} onClick={submit}>
            {busy ? "Đang xử lý…" : "Bỏ qua"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
