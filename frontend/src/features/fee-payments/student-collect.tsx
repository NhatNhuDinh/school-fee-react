import { useCan } from "@refinedev/core";
import { Printer } from "lucide-react";
import React from "react";
import { toast } from "sonner";

import {
  PAYMENT_METHOD_LABEL,
  type BatchDebt,
  type PaymentMethod,
  type Receipt,
  type StudentDebts,
  type StudentHit,
  type TransferInfo,
} from "@/domains/fee-payment/types";
import { DatePicker } from "@/shared/components/form/date-picker";
import { formatNumber } from "@/shared/lib/format";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Checkbox } from "@/shared/ui/checkbox";
import { Label } from "@/shared/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/shared/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/shared/ui/table";
import {
  cancelReceipts,
  collectStudent,
  errorMessage,
  fetchDebts,
  fetchReceipts,
  fetchTransferInfo,
  today,
} from "./api";
import { ConfirmDialog } from "./confirm-dialog";
import { EditLinesDialog } from "./edit-lines-dialog";
import { StudentSearch } from "./student-search";

/**
 * Thu tiền theo học sinh: find the student, tick the đợt thu to settle, take the money.
 *
 * Whole amounts only — the figure on the right is what the ticked khoản thu still owe and cannot
 * be typed over. For a transfer the screen shows the VietQR with the bank account and the
 * description; the money is confirmed by hand once it has arrived.
 */
export function StudentCollect({
  yearId,
  toolbarRight,
  printReceipts,
}: {
  yearId: number;
  toolbarRight: React.ReactNode;
  printReceipts: (receipts: Receipt[]) => void;
}) {
  const [student, setStudent] = React.useState<StudentHit | null>(null);
  const [debts, setDebts] = React.useState<StudentDebts | null>(null);
  const [loadError, setLoadError] = React.useState<string | null>(null);
  const [reload, setReload] = React.useState(0);
  const [chosen, setChosen] = React.useState<Set<number>>(new Set());
  const [excluded, setExcluded] = React.useState<Set<number>>(new Set());
  const [method, setMethod] = React.useState<PaymentMethod>("CASH");
  const [paidOn, setPaidOn] = React.useState(today());
  const [transfer, setTransfer] = React.useState<TransferInfo | null>(null);
  const [editing, setEditing] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const [last, setLast] = React.useState<Receipt[]>([]);
  const [confirmCancel, setConfirmCancel] = React.useState(false);

  const { data: canWrite } = useCan({ resource: "fee_payments", action: "edit" });
  const { data: canDelete } = useCan({ resource: "fee_payments", action: "delete" });

  // What the student owes, again after every change.
  React.useEffect(() => {
    if (!student) {
      setDebts(null);
      return;
    }
    let live = true;
    fetchDebts(yearId, student.enrollmentId)
      .then((result) => {
        if (!live) return;
        setLoadError(null);
        setDebts(result);
        setChosen(new Set(result.batches.filter((b) => b.outstanding > 0).map((b) => b.batchId)));
        setExcluded(new Set());
      })
      .catch((error) => live && setLoadError(errorMessage(error)));
    return () => {
      live = false;
    };
  }, [student, yearId, reload]);

  const batches = debts?.batches ?? [];
  const shown = batches.filter((batch) => chosen.has(batch.batchId));
  const picked = shown.flatMap((batch) => batch.lines.filter((l) => l.outstanding > 0 && !excluded.has(l.receivableId)));
  const amount = picked.reduce((sum, line) => sum + line.outstanding, 0);
  const receivable = shown.reduce((sum, batch) => sum + batch.receivable, 0);
  const collected = shown.reduce((sum, batch) => sum + batch.collected, 0);

  // The QR follows the amount; a fresh description each time the amount or the student changes.
  React.useEffect(() => {
    if (method !== "TRANSFER" || amount <= 0) {
      setTransfer(null);
      return;
    }
    let live = true;
    fetchTransferInfo(yearId, amount)
      .then((info) => live && setTransfer(info))
      .catch(() => live && setTransfer(null));
    return () => {
      live = false;
    };
  }, [method, amount, yearId, student?.enrollmentId, reload]);

  function toggleBatch(batch: BatchDebt, on: boolean) {
    const next = new Set(chosen);
    if (on) next.add(batch.batchId);
    else next.delete(batch.batchId);
    setChosen(next);
  }

  async function collect(thenPrint: boolean) {
    if (!student || picked.length === 0) return;
    setBusy(true);
    try {
      const result = await collectStudent({
        academicYearId: yearId,
        enrollmentId: student.enrollmentId,
        method,
        paidOn,
        receivableIds: picked.map((line) => line.receivableId),
        transferContent: method === "TRANSFER" ? transfer?.content : undefined,
      });
      setLast(result.receipts);
      toast.success(
        method === "TRANSFER" ? "Đã ghi nhận tiền chuyển khoản" : "Đã thu tiền",
        { description: `${result.receipts.map((r) => r.receiptCode).join(", ")} · ${formatNumber(result.total)} đ` }
      );
      if (thenPrint) printReceipts(result.receipts);
      setReload((n) => n + 1);
    } catch (error) {
      toast.error(errorMessage(error));
      setReload((n) => n + 1);
    } finally {
      setBusy(false);
    }
  }

  async function cancelLast() {
    setBusy(true);
    try {
      await cancelReceipts(yearId, last.map((r) => r.id));
      toast.success("Đã hủy phiếu thu", { description: last.map((r) => r.receiptCode).join(", ") });
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
    if (last.length === 0) return;
    try {
      printReceipts(await fetchReceipts(yearId, last.map((r) => r.id)));
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  const canCollect = Boolean(canWrite?.can) && picked.length > 0 && !busy && (method === "CASH" || transfer !== null);
  const transferLabels = method === "TRANSFER";

  return (
    <>
      <Card className="flex min-h-0 flex-1 flex-col gap-0 overflow-hidden p-0">
        <div className="flex flex-wrap items-center gap-3 border-b p-3">
          <div className="min-w-72 flex-1">
            <StudentSearch
              yearId={yearId}
              label={student ? `${student.fullName} (${student.studentCode}) · ${student.className}` : null}
              onPick={(hit) => {
                setStudent(hit);
                setLast([]);
              }}
              onClear={() => {
                setStudent(null);
                setLast([]);
              }}
            />
          </div>
          <div className="flex items-center gap-2">{toolbarRight}</div>
        </div>

        {!student ? (
          <div className="text-muted-foreground flex flex-1 items-center justify-center p-10 text-center text-sm">
            Nhập tên hoặc mã học sinh để thu tiền. Chỉ những học sinh còn khoản phải thu mới hiện ra.
          </div>
        ) : loadError ? (
          <div className="text-destructive flex flex-1 items-center justify-center p-10 text-sm">{loadError}</div>
        ) : (
          <div className="grid min-h-0 flex-1 grid-cols-[18rem_minmax(0,1fr)_20rem]">
            {/* Đợt thu */}
            <div className="flex min-h-0 flex-col border-r">
              <label className="bg-table-header text-table-header-foreground flex items-center gap-3 border-b px-3 py-2.5 text-sm font-semibold">
                <Checkbox
                  checked={
                    batches.filter((b) => b.outstanding > 0).length > 0 &&
                    batches.filter((b) => b.outstanding > 0).every((b) => chosen.has(b.batchId))
                  }
                  onCheckedChange={(value) =>
                    setChosen(new Set(value === true ? batches.filter((b) => b.outstanding > 0).map((b) => b.batchId) : []))
                  }
                />
                Đợt thu
              </label>
              <div className="min-h-0 flex-1 overflow-y-auto">
                {batches.map((batch) => {
                  const paid = batch.outstanding <= 0;
                  return (
                    <label
                      key={batch.batchId}
                      className={cn(
                        "flex items-start gap-3 border-b px-3 py-2.5 text-sm",
                        paid ? "text-muted-foreground cursor-default" : "cursor-pointer",
                        chosen.has(batch.batchId) && "bg-primary/5"
                      )}
                    >
                      <Checkbox
                        className="mt-0.5"
                        disabled={paid}
                        checked={chosen.has(batch.batchId)}
                        onCheckedChange={(value) => toggleBatch(batch, value === true)}
                      />
                      <span className="min-w-0">
                        <span className="block">
                          {batch.batchName}
                          {batch.scopeLabel ? ` · ${batch.scopeLabel}` : ""}
                        </span>
                        <span className="text-muted-foreground block">
                          {paid ? "Đã thu đủ" : `Còn phải thu: ${formatNumber(batch.outstanding)} đ`}
                        </span>
                      </span>
                    </label>
                  );
                })}
              </div>
            </div>

            {/* Chứng từ */}
            <div className="flex min-h-0 flex-col px-6 py-5">
              <h2 className="shrink-0 text-center text-lg font-bold tracking-tight">CHỨNG TỪ THU TIỀN</h2>
              <dl className="mt-4 grid shrink-0 grid-cols-[auto_1fr_auto_auto] gap-x-3 gap-y-1 text-sm">
                <dt className="text-muted-foreground">Họ tên:</dt>
                <dd className="font-semibold">
                  {debts?.fullName} ({debts?.studentCode})
                </dd>
                <dt className="text-muted-foreground">Số chứng từ:</dt>
                <dd className="text-muted-foreground">Tự sinh khi thu</dd>
                <dt className="text-muted-foreground">Lớp:</dt>
                <dd className="font-semibold">
                  {debts?.className}
                </dd>
              </dl>

              <div className="mt-4 flex min-h-0 flex-1 flex-col overflow-hidden rounded-md border">
                <Table containerClassName="min-h-0 flex-1 overflow-auto [&_td]:h-9 [&_td]:py-1">
                  <TableHeader className="sticky top-0 z-10">
                    <TableRow>
                      <TableHead>Khoản thu</TableHead>
                      <TableHead className="w-14 text-right">SL</TableHead>
                      <TableHead className="w-32 text-right">Còn phải thu</TableHead>
                      <TableHead className="w-28 text-right">Đã thu</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {shown.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={4} className="text-muted-foreground py-6 text-center">
                          Chọn đợt thu ở bên trái.
                        </TableCell>
                      </TableRow>
                    ) : (
                      shown.map((batch) => (
                        <React.Fragment key={batch.batchId}>
                          <TableRow className="bg-muted/60 hover:bg-muted/60">
                            <TableCell colSpan={4} className="text-muted-foreground font-semibold">
                              {batch.batchName}
                              {batch.scopeLabel ? ` · ${batch.scopeLabel}` : ""}
                            </TableCell>
                          </TableRow>
                          {batch.lines.map((line) => (
                            <TableRow
                              key={line.receivableId}
                              className={cn(excluded.has(line.receivableId) && "text-muted-foreground line-through")}
                            >
                              <TableCell>
                                {line.itemName}
                                {line.unitName ? ` (${line.unitName})` : ""}
                              </TableCell>
                              <TableCell className="text-right tabular-nums">{line.quantity}</TableCell>
                              <TableCell className="text-right tabular-nums">{formatNumber(line.outstanding)}</TableCell>
                              <TableCell className="text-right tabular-nums">{formatNumber(line.collected)}</TableCell>
                            </TableRow>
                          ))}
                        </React.Fragment>
                      ))
                    )}
                  </TableBody>
                </Table>
              </div>

              <div className="mt-4 flex shrink-0 flex-wrap items-baseline gap-x-10 gap-y-2 text-sm">
                <span className="text-muted-foreground">
                  Thành tiền: <b className="text-foreground text-lg">{formatNumber(receivable)}</b>
                </span>
                <span className="text-muted-foreground">
                  Còn phải thu: <b className="text-primary text-lg">{formatNumber(amount)}</b>
                </span>
                <span className="text-muted-foreground">
                  Đã thu: <b className="text-foreground text-lg">{formatNumber(collected)}</b>
                </span>
              </div>
            </div>

            {/* Hình thức */}
            <div className="flex min-h-0 flex-col gap-4 overflow-y-auto border-l p-5">
              <div className="grid gap-1.5">
                <Label>Hình thức thu *</Label>
                <Select value={method} onValueChange={(value) => setMethod(value as PaymentMethod)}>
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {(Object.keys(PAYMENT_METHOD_LABEL) as PaymentMethod[]).map((key) => (
                      <SelectItem key={key} value={key}>
                        {PAYMENT_METHOD_LABEL[key]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-1.5">
                <Label>Ngày thu *</Label>
                <DatePicker value={paidOn} onChange={setPaidOn} />
              </div>
              <div className="grid gap-1.5">
                <Label className="text-muted-foreground">Số tiền thu</Label>
                <div className="bg-muted/50 text-primary rounded-md border px-3 py-2.5 text-right text-xl font-bold tabular-nums">
                  {formatNumber(amount)}
                </div>
              </div>

              {method === "TRANSFER" && amount > 0 ? (
                transfer?.configured ? (
                  <div className="grid gap-3">
                    <div className="flex gap-3 rounded-md border p-3">
                      {transfer.qrUrl ? (
                        <img src={transfer.qrUrl} alt="Mã QR chuyển khoản" className="size-32 shrink-0" />
                      ) : null}
                      <dl className="min-w-0 text-xs">
                        <Bank label="Ngân hàng" value={transfer.bank} />
                        <Bank label="Số tài khoản" value={transfer.accountNumber} />
                        <Bank label="Chủ tài khoản" value={transfer.accountHolder} />
                        <Bank label="Nội dung" value={transfer.content} />
                      </dl>
                    </div>
                    <p className="text-muted-foreground text-xs">Quét mã QR để chuyển khoản đúng số tiền và nội dung</p>
                    <p className="flex items-center gap-2 text-xs">
                      <span className="bg-warning size-2 rounded-full" />
                      Chờ tiền về. Bấm “Đã nhận tiền” khi đã nhận.
                    </p>
                  </div>
                ) : (
                  <p className="text-destructive text-xs">
                    Chưa cấu hình tài khoản ngân hàng của trường nên không tạo được mã QR.
                  </p>
                )
              ) : null}
            </div>
          </div>
        )}
      </Card>

      <div data-print="hide" className="flex shrink-0 flex-wrap items-center justify-end gap-2">
        <Button type="button" variant="outline" size="icon" aria-label="In lại phiếu vừa thu" disabled={last.length === 0} onClick={printLast}>
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
        <Button type="button" variant="outline" disabled={!student || batches.length === 0} onClick={() => setEditing(true)}>
          Sửa chi tiết
        </Button>
        <Button
          type="button"
          variant="outline"
          className="border-primary text-primary hover:text-primary"
          disabled={!canCollect}
          onClick={() => collect(true)}
        >
          {transferLabels ? "Đã nhận & In" : "Thu tiền & In"}
        </Button>
        <Button type="button" disabled={!canCollect} onClick={() => collect(false)}>
          {transferLabels ? "Đã nhận tiền" : "Thu tiền"}
        </Button>
      </div>

      <EditLinesDialog
        open={editing}
        batches={shown}
        excluded={excluded}
        onChange={setExcluded}
        onClose={() => setEditing(false)}
      />
      <ConfirmDialog
        open={confirmCancel}
        title="Hủy phiếu thu vừa lập?"
        description={`Các phiếu ${last.map((r) => r.receiptCode).join(", ")} sẽ bị hủy và khoản nợ được trả lại cho học sinh.`}
        confirmLabel="Hủy thu tiền"
        destructive
        busy={busy}
        onConfirm={cancelLast}
        onClose={() => setConfirmCancel(false)}
      />
    </>
  );
}

function Bank({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="mb-1.5">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-semibold break-all">{value}</dd>
    </div>
  );
}
