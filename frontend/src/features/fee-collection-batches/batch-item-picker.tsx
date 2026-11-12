import { TriangleAlert } from "lucide-react";

import {
  priceRange,
  type FeeCollectionBatchPreview,
  type PreviewCandidate,
} from "@/domains/fee-collection-batch/types";
import { formatCurrency, formatNumber } from "@/shared/lib/format";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { Checkbox } from "@/shared/ui/checkbox";
import { Input } from "@/shared/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/shared/ui/table";

/** One line of the picker: a khoản thu that can be in the đợt. */
export type PickerRow = {
  feeItemId: number;
  name: string;
  unitName: string;
  mandatory: boolean;
  /** "Toàn trường · 128 học sinh đã đăng ký" — whom it would be charged to. */
  detail: string;
  minUnitPrice: number | null;
  maxUnitPrice: number | null;
  studentsWithoutPrice: number;
  quantityEditable: boolean;
  /** Why this line cannot be unticked, when it cannot. */
  lockedReason?: string;
};

export function rowFromCandidate(candidate: PreviewCandidate): PickerRow {
  const where =
    candidate.scope === "CLASS_GROUP"
      ? (candidate.scopeName ?? "Theo khối")
      : candidate.scope === "CLASS"
        ? `Lớp ${candidate.scopeName ?? ""}`.trim()
        : "Toàn trường";
  const who = candidate.mandatory
    ? `${formatNumber(candidate.applicableStudents)} học sinh`
    : `${formatNumber(candidate.applicableStudents)} học sinh đã đăng ký`;
  return {
    feeItemId: candidate.feeItemId,
    name: candidate.name,
    unitName: candidate.unitName,
    mandatory: candidate.mandatory,
    detail: `${where} · ${who}`,
    minUnitPrice: candidate.minUnitPrice,
    maxUnitPrice: candidate.maxUnitPrice,
    studentsWithoutPrice: candidate.studentsWithoutPrice,
    quantityEditable: candidate.quantityEditable,
  };
}

function priceText(row: PickerRow): string {
  switch (priceRange(row.minUnitPrice, row.maxUnitPrice)) {
    case "single":
      return `${formatCurrency(row.minUnitPrice)} / ${row.unitName}`;
    case "range":
      return "Nhiều đơn giá";
    default:
      return "Chưa có đơn giá";
  }
}

type Props = {
  rows: PickerRow[];
  /** The khoản thu chosen, as khoản thu id to quantity. */
  selected: Record<number, number>;
  onToggle: (feeItemId: number, on: boolean) => void;
  onQuantity: (feeItemId: number, quantity: number) => void;
  /** What to say when there is nothing to choose from. */
  empty: string;
  className?: string;
};

/**
 * The khoản thu a đợt thu can collect, as a table to tick: name and whom it reaches, whether
 * it is mandatory, the price, and the quantity. Shared by making a đợt and by changing the
 * khoản thu of one that exists, because they are the same choice.
 *
 * A quantity is editable only for a unit that is not a period: a khoản thu counted in
 * tháng, quý, kỳ or năm is charged once per period, so its quantity is fixed at 1 and the
 * input says so rather than hiding.
 */
export function BatchItemPicker({ rows, selected, onToggle, onQuantity, empty, className }: Props) {
  if (rows.length === 0) {
    return (
      <div className={cn("text-muted-foreground rounded-lg border border-dashed p-6 text-center text-sm", className)}>
        {empty}
      </div>
    );
  }
  return (
    <div className={cn("overflow-hidden rounded-lg border", className)}>
      <Table>
        <TableHeader>
          <TableRow className="bg-surface-subtle hover:bg-surface-subtle">
            <TableHead className="w-10" />
            <TableHead>Khoản thu</TableHead>
            <TableHead className="w-28">Loại</TableHead>
            <TableHead className="w-48">Đơn giá</TableHead>
            <TableHead className="w-32">Số lượng</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => {
            const checked = selected[row.feeItemId] !== undefined;
            const quantity = selected[row.feeItemId] ?? 1;
            return (
              <TableRow key={row.feeItemId} data-state={checked ? "selected" : undefined}>
                <TableCell>
                  <Checkbox
                    aria-label={`Chọn ${row.name}`}
                    checked={checked}
                    disabled={Boolean(row.lockedReason)}
                    title={row.lockedReason}
                    onCheckedChange={(next) => onToggle(row.feeItemId, next === true)}
                  />
                </TableCell>
                <TableCell>
                  <div className="flex flex-col leading-tight">
                    <span className="font-medium">{row.name}</span>
                    <span className="text-muted-foreground text-xs">{row.detail}</span>
                    {row.lockedReason && (
                      <span className="text-muted-foreground text-xs">{row.lockedReason}</span>
                    )}
                    {row.studentsWithoutPrice > 0 && (
                      <span className="text-destructive mt-0.5 flex items-center gap-1 text-xs">
                        <TriangleAlert className="size-3" />
                        Thiếu đơn giá cho {row.studentsWithoutPrice} học sinh
                      </span>
                    )}
                  </div>
                </TableCell>
                <TableCell>
                  <Badge variant={row.mandatory ? "warning" : "neutral"}>
                    {row.mandatory ? "Bắt buộc" : "Tự chọn"}
                  </Badge>
                </TableCell>
                <TableCell>{priceText(row)}</TableCell>
                <TableCell>
                  <Input
                    type="number"
                    min={1}
                    inputMode="numeric"
                    aria-label={`Số lượng ${row.name}`}
                    className="h-8 w-20 tabular-nums"
                    value={quantity}
                    disabled={!checked || !row.quantityEditable}
                    title={row.quantityEditable ? undefined : `Tính theo ${row.unitName}, số lượng luôn là 1`}
                    onChange={(event) => {
                      const next = Math.floor(Number(event.target.value));
                      onQuantity(row.feeItemId, Number.isFinite(next) && next >= 1 ? next : 1);
                    }}
                  />
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}

/** The figures under the picker: who is charged, how many khoản thu, and the total. */
export function PreviewStrip({
  preview,
  pending,
  label = "Tổng phải thu dự kiến",
}: {
  preview: FeeCollectionBatchPreview | undefined;
  pending: boolean;
  label?: string;
}) {
  const problems = preview?.problems ?? [];
  return (
    <div className="space-y-2">
      <div
        className={cn(
          "bg-surface-subtle flex items-center justify-between gap-6 rounded-lg border px-4 py-3 transition-opacity",
          pending && "opacity-60"
        )}
        aria-live="polite"
      >
        <div className="flex items-baseline gap-6 text-sm">
          <span className="flex items-baseline gap-2">
            <span className="text-muted-foreground text-xs">Học sinh</span>
            <strong className="text-lg tabular-nums">{formatNumber(preview?.studentCount ?? 0)}</strong>
          </span>
          <span className="flex items-baseline gap-2">
            <span className="text-muted-foreground text-xs">Khoản thu</span>
            <strong className="text-lg tabular-nums">{formatNumber(preview?.itemCount ?? 0)}</strong>
          </span>
        </div>
        <div className="text-end">
          <div className="text-muted-foreground text-xs">{label}</div>
          <div className="text-primary text-2xl leading-tight font-bold tabular-nums">
            {problems.length > 0 ? "Chưa tính được" : formatCurrency(preview?.receivableAmount ?? 0)}
          </div>
        </div>
      </div>
      {problems.map((problem) => (
        <p key={problem} className="text-destructive flex items-start gap-1.5 text-xs" role="alert">
          <TriangleAlert className="mt-0.5 size-3.5 shrink-0" />
          {problem}
        </p>
      ))}
    </div>
  );
}
