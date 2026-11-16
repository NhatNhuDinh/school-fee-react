import { useCan } from "@refinedev/core";
import { CopyIcon, EyeIcon, RefreshCwIcon, SearchIcon, Undo2Icon } from "lucide-react";
import React from "react";
import { toast } from "sonner";

import {
  REASON_LABEL,
  type BankRow,
  type StudentDebts,
  type StudentHit,
} from "@/domains/fee-payment/types";
import { FEE_PAYMENTS, onConnected, subscribe } from "@/shared/api/live";
import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
import { NoAcademicYear } from "@/shared/components/academic-year/no-academic-year";
import { DateRangePicker, thisMonth, type DateRangeValue } from "@/shared/components/form/date-range-picker";
import { ListView, ListViewHeader } from "@/shared/components/views/list-view";
import { formatDateTime, formatNumber } from "@/shared/lib/format";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Checkbox } from "@/shared/ui/checkbox";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/shared/ui/dialog";
import { Input } from "@/shared/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/shared/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/shared/ui/select";
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from "@/shared/ui/table";
import { Textarea } from "@/shared/ui/textarea";
import {
  assignBankRow,
  checkBank,
  describeCheck,
  errorMessage,
  fetchBankRows,
  fetchDebts,
  fetchPaymentSummary,
  ignoreBankRow,
  undoBankRow,
  type PaymentSummary,
} from "./api";
import { ConfirmDialog } from "./confirm-dialog";
import { StudentSearch } from "./student-search";

type Tab = "all" | "processed";

/** How a line stands, in the words and the colour the screen uses for it. */
const STATUS: Record<BankRow["status"], { label: string; variant: "warning" | "success" | "info" | "neutral" }> = {
  NEEDS_REVIEW: { label: "Cần rà soát", variant: "warning" },
  MATCHED: { label: "Đã khớp tự động", variant: "success" },
  RESOLVED: { label: "Đã gán thủ công", variant: "info" },
  IGNORED: { label: "Bỏ qua", variant: "neutral" },
};

/** What the status filter offers: every status in "Tất cả giao dịch", only the settled ones in "Đã xử lý". */
const STATUS_CHOICES: { value: string; label: string; settled: boolean }[] = [
  { value: "ALL", label: "Tất cả", settled: true },
  { value: "NEEDS_REVIEW", label: STATUS.NEEDS_REVIEW.label, settled: false },
  { value: "MATCHED", label: STATUS.MATCHED.label, settled: true },
  { value: "RESOLVED", label: STATUS.RESOLVED.label, settled: true },
  { value: "IGNORED", label: STATUS.IGNORED.label, settled: true },
];

/** The reason a line waits, or who dealt with it: the second thing to know after its status. */
function detailOf(row: BankRow): string {
  if (row.status === "NEEDS_REVIEW") return row.reason ? (REASON_LABEL[row.reason] ?? row.reason) : "—";
  if (row.status === "MATCHED") return "Tự động bởi SePay";
  if (row.status === "RESOLVED") return row.handledBy ?? "—";
  // Set aside: by a person (with their note), or by the system because it was never a payment to us.
  if (row.reason === "MANUAL_IGNORED") return [row.handledBy, row.note].filter(Boolean).join(" · ") || "—";
  return row.reason ? (REASON_LABEL[row.reason] ?? row.reason) : "—";
}

/** A search box that tells the page what was typed only once typing pauses, so every keystroke is not a request. */
function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = React.useState(value);
  React.useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

/**
 * Rà soát giao dịch: every line the bank reported, and what became of it. The bank's report settles a matching
 * transfer by itself; this screen is for seeing that, and for the rest — a line that matched nothing, a short
 * payment, a webhook that never came. Two tabs: all of it, and what has been dealt with (by the system or a person).
 */
export const BankReviewPage = () => {
  const { enabled, selected: year, isLoading } = useAcademicYear();
  if (!enabled) {
    return (
      <ListView>
        <ListViewHeader title="Rà soát giao dịch" canCreate={false} compact />
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
  const [tab, setTab] = React.useState<Tab>("all");
  const [reload, setReload] = React.useState(0);
  const [rows, setRows] = React.useState<BankRow[]>([]);
  const [counts, setCounts] = React.useState<PaymentSummary>({ review: 0, waiting: 0, all: 0, processed: 0 });
  const [range, setRange] = React.useState<DateRangeValue>(thisMonth);
  const [search, setSearch] = React.useState("");
  const [status, setStatus] = React.useState("ALL");
  const [checking, setChecking] = React.useState(false);
  const [assigning, setAssigning] = React.useState<BankRow | null>(null);
  const [ignoring, setIgnoring] = React.useState<BankRow | null>(null);
  const [undoing, setUndoing] = React.useState<BankRow | null>(null);
  const [busy, setBusy] = React.useState(false);
  const { data: canWrite } = useCan({ resource: "fee_payments", action: "edit" });
  const q = useDebounced(search);

  const refresh = React.useCallback(() => setReload((n) => n + 1), []);

  React.useEffect(() => {
    let live = true;
    const filter = { from: range.from, to: range.to, q };
    fetchPaymentSummary(yearId, filter)
      .then((summary) => live && setCounts(summary))
      .catch(() => undefined);
    fetchBankRows(yearId, tab, { ...filter, status: status === "ALL" ? undefined : status })
      .then((list) => live && setRows(list))
      .catch((error) => live && toast.error(errorMessage(error)));
    return () => {
      live = false;
    };
  }, [yearId, tab, range, q, status, reload]);

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

  function changeTab(next: Tab) {
    setTab(next);
    // "Cần rà soát" is not a status of the settled tab; do not leave the list filtered by something it cannot show.
    if (next === "processed" && status === "NEEDS_REVIEW") setStatus("ALL");
  }

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

  async function undo() {
    if (!undoing) return;
    setBusy(true);
    try {
      await undoBankRow(yearId, undoing.id);
      toast.success("Đã hoàn tác", { description: "Giao dịch đã quay về danh sách cần rà soát." });
      setUndoing(null);
      refresh();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  const tabs: { key: Tab; label: string; count: number }[] = [
    { key: "all", label: "Tất cả giao dịch", count: counts.all },
    { key: "processed", label: "Đã xử lý", count: counts.processed },
  ];
  const choices = STATUS_CHOICES.filter((choice) => tab === "all" || choice.settled);
  const settledTab = tab === "processed";

  return (
    <ListView className="h-[calc(100svh-4rem-2*var(--shell-gutter-y))] min-h-[36rem]">
      <ListViewHeader
        title="Rà soát giao dịch"
        canCreate={false}
        compact
        actions={
          <Button type="button" variant="outline" disabled={checking || !canWrite?.can} onClick={checkAll}>
            <RefreshCwIcon className={cn(checking && "animate-spin")} />
            {checking ? "Đang kiểm tra…" : "Kiểm tra giao dịch"}
          </Button>
        }
      />
      <Card className="flex min-h-0 flex-1 flex-col gap-0 overflow-hidden p-0">
        <div role="tablist" className="flex shrink-0 gap-1 border-b px-3">
          {tabs.map((item) => (
            <button
              key={item.key}
              type="button"
              role="tab"
              aria-selected={tab === item.key}
              onClick={() => changeTab(item.key)}
              className={cn(
                "focus-ring relative -mb-px flex h-control items-center gap-2 px-3 text-sm whitespace-nowrap",
                tab === item.key
                  ? "text-foreground font-semibold after:bg-brand after:absolute after:inset-x-3 after:bottom-0 after:h-0.5 after:rounded-full"
                  : "text-neutral-text hover:text-foreground"
              )}
            >
              {item.label}
              <span className="text-muted-foreground text-xs font-normal tabular-nums">{item.count}</span>
            </button>
          ))}
        </div>

        <div className="flex shrink-0 flex-wrap items-center gap-2 p-3">
          <div className="relative w-96 max-w-full">
            <SearchIcon className="text-muted-foreground pointer-events-none absolute start-2.5 top-1/2 size-4 -translate-y-1/2" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm theo nội dung CK, mã giao dịch, tên học sinh…"
              aria-label="Tìm giao dịch"
              className="ps-8"
            />
          </div>
          <DateRangePicker value={range} onChange={setRange} />
          <Select value={status} onValueChange={setStatus}>
            <SelectTrigger className="w-52" aria-label="Trạng thái">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {choices.map((choice) => (
                <SelectItem key={choice.value} value={choice.value}>
                  {choice.value === "ALL" ? "Trạng thái: Tất cả" : choice.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex min-h-0 flex-1 flex-col">
          <Table containerClassName="min-h-0 flex-1 overflow-auto">
            <TableHeader className="bg-card sticky top-0 z-10">
              <TableRow>
                <TableHead className="w-12">STT</TableHead>
                <TableHead>Thời gian</TableHead>
                <TableHead>Nội dung chuyển khoản</TableHead>
                <TableHead>Học sinh</TableHead>
                <TableHead>Lớp</TableHead>
                <TableHead align="right">
                  Số tiền (VND)
                </TableHead>
                <TableHead>{settledTab ? "Kết quả" : "Trạng thái"}</TableHead>
                <TableHead>{settledTab ? "Người xử lý" : "Lý do / người xử lý"}</TableHead>
                <TableHead>Thao tác</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow className="hover:bg-transparent">
                  <TableCell colSpan={9} className="text-muted-foreground py-10 text-center">
                    {q || status !== "ALL"
                      ? "Không có giao dịch nào khớp bộ lọc."
                      : settledTab
                        ? "Chưa có giao dịch nào được xử lý trong khoảng ngày này."
                        : "Không có giao dịch nào trong khoảng ngày này."}
                  </TableCell>
                </TableRow>
              ) : (
                rows.map((row, index) => (
                  <TableRow key={row.id}>
                    <TableCell className="text-muted-foreground tabular-nums">{index + 1}</TableCell>
                    <TableCell>{formatDateTime(row.time)}</TableCell>
                    <TableCell>
                      <ContentCell row={row} />
                    </TableCell>
                    <TableCell className="max-w-36 truncate font-semibold" title={row.suggestedStudentName ?? undefined}>
                      {row.suggestedStudentName ?? "—"}
                    </TableCell>
                    <TableCell>{row.suggestedClassName ?? "—"}</TableCell>
                    <TableCell align="right" className="font-semibold tabular-nums">
                      {formatNumber(row.amount)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={STATUS[row.status].variant}>{STATUS[row.status].label}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground max-w-36 truncate" title={detailOf(row)}>
                      {detailOf(row)}
                    </TableCell>
                    <TableCell>
                      {canWrite?.can ? (
                        <span className="text-link flex gap-4 text-sm font-semibold">
                          {row.status === "NEEDS_REVIEW" ? (
                            <>
                              <button type="button" className="hover:underline" onClick={() => setAssigning(row)}>
                                Gán thủ công
                              </button>
                              <button type="button" className="text-foreground font-normal hover:underline" onClick={() => setIgnoring(row)}>
                                Bỏ qua
                              </button>
                            </>
                          ) : null}
                          {row.undoable ? (
                            <button type="button" className="flex items-center gap-1.5 hover:underline" onClick={() => setUndoing(row)}>
                              <Undo2Icon className="size-4" />
                              Hoàn tác
                            </button>
                          ) : null}
                        </span>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
            <TableFooter className="bg-surface-subtle sticky bottom-0 z-10">
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={9} className="text-muted-foreground h-9 text-xs font-normal">
                  Hiển thị {rows.length > 0 ? `1–${rows.length} / ${rows.length}` : "0"} giao dịch
                  {rows.length >= 300 ? " (300 giao dịch gần nhất, hãy thu hẹp khoảng ngày để xem các giao dịch cũ hơn)" : ""}
                </TableCell>
              </TableRow>
            </TableFooter>
          </Table>
        </div>
      </Card>

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
        open={undoing !== null}
        title="Hoàn tác xử lý này?"
        description={
          undoing?.status === "RESOLVED"
            ? `Giao dịch ${formatNumber(undoing.amount)} VND${undoing.suggestedStudentName ? ` của ${undoing.suggestedStudentName}` : ""} sẽ quay về danh sách cần rà soát. Phiếu thu đã tạo khi bạn gán thủ công sẽ bị hủy và khoản nợ của học sinh được trả lại.`
            : `Giao dịch ${formatNumber(undoing?.amount ?? 0)} VND sẽ quay về danh sách cần rà soát. Không có phiếu thu nào bị ảnh hưởng.`
        }
        confirmLabel="Hoàn tác"
        cancelLabel="Giữ nguyên"
        busy={busy}
        onConfirm={undo}
        onClose={() => setUndoing(null)}
      />
    </ListView>
  );
}

/**
 * The transfer memo, cut to one line so a long one does not make the row tall, with an eye that opens all of it.
 * A bank memo is where the order code and the student's name are, and either may be at the far end.
 */
function ContentCell({ row }: { row: BankRow }) {
  const content = row.content ?? "";
  if (!content) return <span className="text-muted-foreground">—</span>;

  async function copy() {
    try {
      await navigator.clipboard.writeText(content);
      toast.success("Đã sao chép nội dung chuyển khoản");
    } catch {
      toast.error("Không sao chép được, hãy chọn và sao chép thủ công");
    }
  }

  return (
    <div className="flex w-60 items-center gap-2">
      <span className="min-w-0 flex-1 truncate" title={content}>
        {content}
      </span>
      <Popover>
        <PopoverTrigger asChild>
          <button
            type="button"
            aria-label="Xem đầy đủ nội dung chuyển khoản"
            className="focus-ring text-muted-foreground hover:bg-accent hover:text-foreground flex size-6 shrink-0 items-center justify-center rounded-sm"
          >
            <EyeIcon className="size-4" />
          </button>
        </PopoverTrigger>
        <PopoverContent align="start" className="w-[28rem] max-w-[90vw] space-y-2.5 p-4">
          <div className="text-sm font-semibold">Nội dung chuyển khoản</div>
          <div className="text-muted-foreground text-xs">
            {row.sepayId ? `Giao dịch #${row.sepayId} · ` : ""}
            {formatDateTime(row.time)} · {formatNumber(row.amount)} VND
          </div>
          <div className="bg-muted rounded-sm p-3 font-mono text-[13px] leading-5 break-words select-all">{content}</div>
          <Button type="button" variant="outline" onClick={copy}>
            <CopyIcon />
            Sao chép
          </Button>
        </PopoverContent>
      </Popover>
    </div>
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
  const allPicked = lines.length > 0 && lines.every((l) => picked.has(l.receivableId));
  const somePicked = !allPicked && lines.some((l) => picked.has(l.receivableId));

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
          // `minmax(0, 1fr)`: a grid column otherwise grows to its widest child, and a bank memo is one long word.
          <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-4">
            <p className="text-sm">
              Giao dịch <b>{formatNumber(row.amount)} VND</b> lúc {formatDateTime(row.time)}
              {row.content ? <span className="text-muted-foreground break-all"> · “{row.content}”</span> : null}
            </p>
            <StudentSearch
              yearId={yearId}
              openOnFocus={false}
              fitWidth
              label={debts ? `${debts.fullName} (${debts.studentCode})${debts.className ? ` · ${debts.className}` : ""}` : null}
              onPick={(hit: StudentHit) => setEnrollmentId(hit.enrollmentId)}
              onClear={() => {
                setEnrollmentId(null);
                setDebts(null);
                setPicked(new Set());
              }}
            />
            <div className="overflow-hidden rounded-md border">
              {lines.length > 0 ? (
                // One box for all of them: a student can owe a dozen khoản across several đợt, and ticking them
                // one by one to reach the amount of the line is the slow way to find that it is the whole debt.
                <label className="bg-surface-subtle flex cursor-pointer items-center gap-3 border-b px-3 py-2 text-sm">
                  <Checkbox
                    checked={allPicked ? true : somePicked ? "indeterminate" : false}
                    onCheckedChange={(value) =>
                      setPicked(value === true ? new Set(lines.map((l) => l.receivableId)) : new Set())
                    }
                    aria-label="Chọn tất cả các khoản"
                  />
                  <span className="min-w-0 flex-1 font-semibold">Chọn tất cả</span>
                  <span className="text-muted-foreground text-xs">
                    {picked.size}/{lines.length} khoản
                  </span>
                </label>
              ) : null}
              <div className="max-h-64 overflow-y-auto">
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
            </div>
            <p className={cn("text-sm", exact ? "text-success-text" : "text-destructive")}>
              Đã chọn {formatNumber(total)} VND / giao dịch {formatNumber(row.amount)} VND
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
          {row ? `${formatNumber(row.amount)} VND lúc ${formatDateTime(row.time)}. ` : ""}
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
