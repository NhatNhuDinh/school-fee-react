import { useCan } from "@refinedev/core";
import React from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";

import { PAYMENT_METHOD_LABEL, type History } from "@/domains/fee-payment/types";
import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
import { NoAcademicYear } from "@/shared/components/academic-year/no-academic-year";
import { DatePicker } from "@/shared/components/form/date-picker";
import { ListView, ListViewHeader } from "@/shared/components/views/list-view";
import { useDebounced } from "@/shared/hooks/use-debounced";
import { formatDate, formatNumber } from "@/shared/lib/format";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Checkbox } from "@/shared/ui/checkbox";
import { Input } from "@/shared/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/shared/ui/select";
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from "@/shared/ui/table";
import { cancelReceipts, errorMessage, fetchBatches, fetchHistory, type BatchOption } from "./api";
import { ConfirmDialog } from "./confirm-dialog";

type Preset = "today" | "week" | "month" | "custom";

function iso(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

/** The range a preset stands for; "custom" leaves whatever was typed. */
function rangeOf(preset: Preset): { from: string; to: string } | null {
  const now = new Date();
  if (preset === "today") return { from: iso(now), to: iso(now) };
  if (preset === "week") {
    const monday = new Date(now);
    monday.setDate(now.getDate() - ((now.getDay() + 6) % 7));
    const sunday = new Date(monday);
    sunday.setDate(monday.getDate() + 6);
    return { from: iso(monday), to: iso(sunday) };
  }
  if (preset === "month") {
    return { from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to: iso(new Date(now.getFullYear(), now.getMonth() + 1, 0)) };
  }
  return null;
}

/** Lịch sử thu tiền: every receipt of the year, newest first; the active ones can be cancelled. */
export const PaymentHistory = () => {
  const { enabled, selected: year, isLoading } = useAcademicYear();
  if (!enabled) {
    return (
      <ListView>
        <ListViewHeader title="Lịch sử thu tiền" />
        <Card className="text-muted-foreground p-10 text-center text-sm">
          Thu tiền thuộc một năm học của một trường. Hãy đăng nhập bằng tài khoản của trường để xem.
        </Card>
      </ListView>
    );
  }
  if (!isLoading && !year) return <NoAcademicYear />;
  if (!year) return null;
  return <HistoryOfYear yearId={year.id} />;
};

function HistoryOfYear({ yearId }: { yearId: number }) {
  const navigate = useNavigate();
  const initial = rangeOf("week")!;
  const [preset, setPreset] = React.useState<Preset>("week");
  const [from, setFrom] = React.useState(initial.from);
  const [to, setTo] = React.useState(initial.to);
  const [batchId, setBatchId] = React.useState<number | null>(null);
  const [batches, setBatches] = React.useState<BatchOption[]>([]);
  const [search, setSearch] = React.useState("");
  const q = useDebounced(search, 300);
  const [history, setHistory] = React.useState<History | null>(null);
  const [ticked, setTicked] = React.useState<Set<number>>(new Set());
  const [reload, setReload] = React.useState(0);
  const [confirm, setConfirm] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const { data: canDelete } = useCan({ resource: "fee_payments", action: "delete" });

  React.useEffect(() => {
    fetchBatches(yearId).then(setBatches).catch(() => setBatches([]));
  }, [yearId]);

  React.useEffect(() => {
    let live = true;
    fetchHistory(yearId, { from, to, batchId, q })
      .then((result) => {
        if (!live) return;
        setHistory(result);
        setTicked(new Set());
      })
      .catch((error) => live && toast.error(errorMessage(error)));
    return () => {
      live = false;
    };
  }, [yearId, from, to, batchId, q, reload]);

  const rows = history?.rows ?? [];
  const cancellable = rows.filter((r) => r.status === "ACTIVE");
  const chosen = rows.filter((r) => ticked.has(r.id));

  async function cancel() {
    setBusy(true);
    try {
      const result = await cancelReceipts(yearId, chosen.map((r) => r.id));
      toast.success(`Đã hủy ${result.cancelled} phiếu thu`);
      setConfirm(false);
      setReload((n) => n + 1);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <ListView className="h-[calc(100svh-4rem-2*var(--shell-gutter-y))] min-h-[36rem]">
      <ListViewHeader title="Lịch sử thu tiền" canCreate={false} />
      <Card className="flex min-h-0 flex-1 flex-col gap-0 overflow-hidden p-0">
        <div className="flex flex-wrap items-center gap-3 border-b p-3">
          <Select
            value={preset}
            onValueChange={(value) => {
              const next = value as Preset;
              setPreset(next);
              const range = rangeOf(next);
              if (range) {
                setFrom(range.from);
                setTo(range.to);
              }
            }}
          >
            <SelectTrigger className="w-36">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="today">Hôm nay</SelectItem>
              <SelectItem value="week">Tuần này</SelectItem>
              <SelectItem value="month">Tháng này</SelectItem>
              <SelectItem value="custom">Tùy chọn</SelectItem>
            </SelectContent>
          </Select>
          <span className="text-muted-foreground text-sm">Từ ngày</span>
          <div className="w-40">
            <DatePicker
              value={from}
              onChange={(value) => {
                setFrom(value);
                setPreset("custom");
              }}
            />
          </div>
          <span className="text-muted-foreground text-sm">Đến ngày</span>
          <div className="w-40">
            <DatePicker
              value={to}
              onChange={(value) => {
                setTo(value);
                setPreset("custom");
              }}
            />
          </div>
          <span className="text-muted-foreground text-sm">Đợt thu</span>
          <Select value={batchId === null ? "all" : String(batchId)} onValueChange={(v) => setBatchId(v === "all" ? null : Number(v))}>
            <SelectTrigger className="w-56">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tất cả các đợt thu</SelectItem>
              {batches.map((batch) => (
                <SelectItem key={batch.id} value={String(batch.id)}>
                  {batch.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input
            className="w-44"
            value={search}
            placeholder="Tên hoặc mã học sinh"
            onChange={(event) => setSearch(event.target.value)}
          />
          <Button
            type="button"
            variant="outline"
            className="ms-auto"
            disabled={chosen.length === 0 || !canDelete?.can}
            onClick={() => setConfirm(true)}
          >
            Hủy thu
          </Button>
        </div>

        <div className="flex min-h-0 flex-1 flex-col">
          <Table containerClassName="min-h-0 flex-1 overflow-auto">
            <TableHeader className="sticky top-0 z-10">
              <TableRow>
                <TableHead className="w-12">
                  <Checkbox
                    checked={cancellable.length > 0 && cancellable.every((r) => ticked.has(r.id))}
                    disabled={cancellable.length === 0}
                    onCheckedChange={(value) => setTicked(new Set(value === true ? cancellable.map((r) => r.id) : []))}
                  />
                </TableHead>
                <TableHead className="w-16">STT</TableHead>
                <TableHead className="w-28">Ngày thu</TableHead>
                <TableHead className="w-36">Số chứng từ</TableHead>
                <TableHead>Học sinh</TableHead>
                <TableHead className="w-24">Lớp</TableHead>
                <TableHead>Đợt thu</TableHead>
                <TableHead className="w-32">Hình thức</TableHead>
                <TableHead className="w-32 text-right">Số đã thu</TableHead>
                <TableHead className="w-44">Người thu</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10} className="text-muted-foreground py-10 text-center">
                    Không có phiếu thu nào trong khoảng này.
                  </TableCell>
                </TableRow>
              ) : (
                rows.map((row, index) => {
                  const cancelled = row.status === "CANCELLED";
                  return (
                    <TableRow key={row.id} className={cn(cancelled && "text-muted-foreground")}>
                      <TableCell>
                        <Checkbox
                          disabled={cancelled}
                          checked={ticked.has(row.id)}
                          onCheckedChange={(value) => {
                            const next = new Set(ticked);
                            if (value === true) next.add(row.id);
                            else next.delete(row.id);
                            setTicked(next);
                          }}
                        />
                      </TableCell>
                      <TableCell className="text-muted-foreground">{index + 1}</TableCell>
                      <TableCell>{formatDate(row.paidOn)}</TableCell>
                      <TableCell>{row.receiptCode}</TableCell>
                      <TableCell className={cn("font-semibold", cancelled && "line-through")}>{row.studentName}</TableCell>
                      <TableCell>{row.className}</TableCell>
                      <TableCell>{row.batchName}</TableCell>
                      <TableCell>{PAYMENT_METHOD_LABEL[row.method]}</TableCell>
                      <TableCell className={cn("text-right font-semibold tabular-nums", cancelled && "line-through")}>
                        {formatNumber(row.amount)}
                      </TableCell>
                      <TableCell>
                        {cancelled ? `Đã hủy${row.cancelledByName ? ` bởi ${row.cancelledByName}` : ""}` : row.collectorName}
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
            <TableFooter className="bg-muted sticky bottom-0 z-10">
              <TableRow className="hover:bg-muted">
                <TableCell colSpan={8} className="font-semibold">
                  Tổng: {history?.total ?? 0} phiếu thu
                </TableCell>
                <TableCell className="text-right font-semibold tabular-nums">{formatNumber(history?.amount ?? 0)}</TableCell>
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

      <ConfirmDialog
        open={confirm}
        title="Hủy các phiếu thu đã chọn?"
        description={`${chosen.length} phiếu thu sẽ bị hủy và khoản nợ được trả lại cho học sinh.`}
        confirmLabel="Hủy thu"
        destructive
        busy={busy}
        onConfirm={cancel}
        onClose={() => setConfirm(false)}
      />
    </ListView>
  );
}
