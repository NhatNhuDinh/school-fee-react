import { useCan } from "@refinedev/core";
import { Printer } from "lucide-react";
import React from "react";
import { toast } from "sonner";
import { useNavigate } from "react-router";

import { classStatus, type ClassRow, type Receipt } from "@/domains/fee-payment/types";
import { DatePicker } from "@/shared/components/form/date-picker";
import { formatNumber } from "@/shared/lib/format";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Checkbox } from "@/shared/ui/checkbox";
import { Label } from "@/shared/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/shared/ui/select";
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from "@/shared/ui/table";
import {
  cancelReceipts,
  collectClasses,
  errorMessage,
  fetchBatches,
  fetchClasses,
  fetchReceipts,
  today,
  type BatchOption,
} from "./api";
import { ConfirmDialog } from "./confirm-dialog";

const STATUS: Record<ReturnType<typeof classStatus>, { label: string; dot: string }> = {
  paid: { label: "Đã thu đủ", dot: "bg-success" },
  partial: { label: "Thu một phần", dot: "bg-warning" },
  none: { label: "Chưa thu", dot: "bg-muted-foreground/50" },
};

/**
 * Thu tiền theo lớp: one đợt thu, the classes of it, and "thu" takes every student of the ticked
 * classes in cash for everything they still owe. A class where some students have already paid
 * reads "thu một phần" — it is the students, not the money, that are whole or not.
 */
export function ClassCollect({
  yearId,
  toolbarRight,
  printReceipts,
}: {
  yearId: number;
  toolbarRight: React.ReactNode;
  printReceipts: (receipts: Receipt[]) => void;
}) {
  const navigate = useNavigate();
  const [batches, setBatches] = React.useState<BatchOption[]>([]);
  const [batchId, setBatchId] = React.useState<number | null>(null);
  const [debt, setDebt] = React.useState<"owing" | "all">("owing");
  const [rows, setRows] = React.useState<ClassRow[]>([]);
  const [ticked, setTicked] = React.useState<Set<number>>(new Set());
  const [paidOn, setPaidOn] = React.useState(today());
  const [reload, setReload] = React.useState(0);
  const [busy, setBusy] = React.useState(false);
  const [confirm, setConfirm] = React.useState<{ thenPrint: boolean } | null>(null);
  const [last, setLast] = React.useState<Receipt[]>([]);
  const [confirmCancel, setConfirmCancel] = React.useState(false);

  const { data: canWrite } = useCan({ resource: "fee_payments", action: "edit" });
  const { data: canDelete } = useCan({ resource: "fee_payments", action: "delete" });

  React.useEffect(() => {
    let live = true;
    fetchBatches(yearId)
      .then((list) => {
        if (!live) return;
        setBatches(list);
        setBatchId((current) => current ?? list[0]?.id ?? null);
      })
      .catch(() => live && setBatches([]));
    return () => {
      live = false;
    };
  }, [yearId]);

  React.useEffect(() => {
    if (batchId === null) {
      setRows([]);
      return;
    }
    let live = true;
    fetchClasses(yearId, batchId)
      .then((list) => {
        if (!live) return;
        setRows(list);
        setTicked((prev) => new Set([...prev].filter((id) => list.some((r) => r.classId === id && r.outstanding > 0))));
      })
      .catch((error) => live && toast.error(errorMessage(error)));
    return () => {
      live = false;
    };
  }, [yearId, batchId, reload]);

  const batchName = batches.find((b) => b.id === batchId)?.name ?? "";
  const visible = debt === "owing" ? rows.filter((r) => r.outstanding > 0) : rows;
  const selectable = visible.filter((r) => r.outstanding > 0);
  const chosen = rows.filter((r) => ticked.has(r.classId));
  const chosenAmount = chosen.reduce((sum, r) => sum + r.outstanding, 0);
  const chosenStudents = chosen.reduce((sum, r) => sum + r.owingStudentCount, 0);

  const totals = visible.reduce(
    (t, r) => ({
      receivable: t.receivable + r.receivable,
      collected: t.collected + r.collected,
      outstanding: t.outstanding + r.outstanding,
    }),
    { receivable: 0, collected: 0, outstanding: 0 }
  );

  async function collect(thenPrint: boolean) {
    if (batchId === null) return;
    setBusy(true);
    try {
      const result = await collectClasses({
        academicYearId: yearId,
        batchId,
        classIds: chosen.map((r) => r.classId),
        paidOn,
      });
      setLast(result.receipts);
      setTicked(new Set());
      toast.success("Đã thu tiền", {
        description: `${result.studentCount} học sinh · ${formatNumber(result.total)} đ`,
      });
      if (thenPrint) printReceipts(result.receipts);
      setReload((n) => n + 1);
    } catch (error) {
      toast.error(errorMessage(error));
      setReload((n) => n + 1);
    } finally {
      setBusy(false);
      setConfirm(null);
    }
  }

  async function cancelLast() {
    setBusy(true);
    try {
      await cancelReceipts(yearId, last.map((r) => r.id));
      toast.success("Đã hủy các phiếu thu vừa lập", { description: `${last.length} phiếu` });
      setLast([]);
      setConfirmCancel(false);
      setReload((n) => n + 1);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  async function printLast() {
    try {
      printReceipts(await fetchReceipts(yearId, last.map((r) => r.id)));
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  const allTicked = selectable.length > 0 && selectable.every((r) => ticked.has(r.classId));
  const canCollect = Boolean(canWrite?.can) && chosen.length > 0 && !busy;

  return (
    <>
      <Card className="flex min-h-0 flex-1 flex-col gap-0 overflow-hidden p-0">
        <div className="flex flex-wrap items-center gap-3 border-b p-3">
          <Select value={debt} onValueChange={(value) => setDebt(value as "owing" | "all")}>
            <SelectTrigger className="w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="owing">Còn phải thu</SelectItem>
              <SelectItem value="all">Tất cả</SelectItem>
            </SelectContent>
          </Select>
          <span className="text-muted-foreground text-sm">Đợt thu</span>
          <Select value={batchId === null ? "" : String(batchId)} onValueChange={(value) => setBatchId(Number(value))}>
            <SelectTrigger className="w-80">
              <SelectValue placeholder="Chọn đợt thu" />
            </SelectTrigger>
            <SelectContent>
              {batches.map((batch) => (
                <SelectItem key={batch.id} value={String(batch.id)}>
                  {batch.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <div className="ms-auto flex items-center gap-2">{toolbarRight}</div>
        </div>

        <div className="flex min-h-0 flex-1 flex-col">
          <Table containerClassName="min-h-0 flex-1 overflow-auto">
            <TableHeader className="sticky top-0 z-10">
              <TableRow>
                <TableHead className="w-12">
                  <Checkbox
                    checked={allTicked}
                    disabled={selectable.length === 0}
                    onCheckedChange={(value) => setTicked(new Set(value === true ? selectable.map((r) => r.classId) : []))}
                  />
                </TableHead>
                <TableHead className="w-16">STT</TableHead>
                <TableHead>Lớp</TableHead>
                <TableHead>Khối</TableHead>
                <TableHead>Đợt thu</TableHead>
                <TableHead className="text-right">Số phải thu</TableHead>
                <TableHead className="text-right">Số đã thu</TableHead>
                <TableHead className="text-right">Số còn phải thu</TableHead>
                <TableHead className="w-40">Trạng thái</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={9} className="text-muted-foreground py-10 text-center">
                    {batchId === null ? "Năm học này chưa có đợt thu." : "Không có lớp nào còn phải thu."}
                  </TableCell>
                </TableRow>
              ) : (
                visible.map((row, index) => {
                  const status = STATUS[classStatus(row)];
                  const done = row.outstanding <= 0;
                  return (
                    <TableRow key={row.classId} className={cn(ticked.has(row.classId) && "bg-primary/5")}>
                      <TableCell>
                        <Checkbox
                          disabled={done}
                          checked={ticked.has(row.classId)}
                          onCheckedChange={(value) => {
                            const next = new Set(ticked);
                            if (value === true) next.add(row.classId);
                            else next.delete(row.classId);
                            setTicked(next);
                          }}
                        />
                      </TableCell>
                      <TableCell className="text-muted-foreground">{index + 1}</TableCell>
                      <TableCell className="font-semibold">{row.className}</TableCell>
                      <TableCell>{row.classGroupName}</TableCell>
                      <TableCell>{batchName}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatNumber(row.receivable)}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatNumber(row.collected)}</TableCell>
                      <TableCell className="text-right font-semibold tabular-nums">{formatNumber(row.outstanding)}</TableCell>
                      <TableCell>
                        <span className="flex items-center gap-2">
                          <span className={cn("size-2 rounded-full", status.dot)} />
                          {status.label}
                        </span>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
            <TableFooter className="bg-muted sticky bottom-0 z-10">
              <TableRow className="hover:bg-muted">
                <TableCell colSpan={5} className="font-semibold">
                  Tổng: {visible.length} lớp
                </TableCell>
                <TableCell className="text-right font-semibold tabular-nums">{formatNumber(totals.receivable)}</TableCell>
                <TableCell className="text-right font-semibold tabular-nums">{formatNumber(totals.collected)}</TableCell>
                <TableCell className="text-primary text-right font-semibold tabular-nums">{formatNumber(totals.outstanding)}</TableCell>
                <TableCell />
              </TableRow>
            </TableFooter>
          </Table>
        </div>

      </Card>

      <div data-print="hide" className="flex shrink-0 flex-wrap items-center gap-2">
        <div className="flex items-center gap-2">
          <Label>Ngày thu *</Label>
          <div className="w-44">
            <DatePicker value={paidOn} onChange={setPaidOn} />
          </div>
        </div>
        <div className="ms-auto flex flex-wrap items-center gap-2">
          <Button type="button" variant="outline" size="icon" aria-label="In lại các phiếu vừa thu" disabled={last.length === 0} onClick={printLast}>
            <Printer className="size-4" />
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={last.length === 0 || busy || !canDelete?.can}
            onClick={() => setConfirmCancel(true)}
          >
            Hủy thu tiền
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={batchId === null}
            onClick={() => navigate(`/fee_collection_batches/show/${batchId}`)}
          >
            Xem chi tiết
          </Button>
          <Button
            type="button"
            variant="outline"
            className="border-primary text-primary hover:text-primary"
            disabled={!canCollect}
            onClick={() => setConfirm({ thenPrint: true })}
          >
            Thu tiền & In
          </Button>
          <Button type="button" disabled={!canCollect} onClick={() => setConfirm({ thenPrint: false })}>
            Thu tiền
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={confirm !== null}
        title="Thu tiền theo lớp?"
        description={`Thu tiền mặt của ${chosenStudents} học sinh thuộc ${chosen.length} lớp trong “${batchName}”, tổng ${formatNumber(chosenAmount)} đ. Mỗi học sinh nhận một phiếu thu cho toàn bộ số còn nợ.`}
        confirmLabel="Thu tiền"
        busy={busy}
        onConfirm={() => confirm && collect(confirm.thenPrint)}
        onClose={() => setConfirm(null)}
      />
      <ConfirmDialog
        open={confirmCancel}
        title="Hủy các phiếu thu vừa lập?"
        description={`${last.length} phiếu thu sẽ bị hủy và khoản nợ được trả lại cho học sinh.`}
        confirmLabel="Hủy thu tiền"
        destructive
        busy={busy}
        onConfirm={cancelLast}
        onClose={() => setConfirmCancel(false)}
      />
    </>
  );
}
