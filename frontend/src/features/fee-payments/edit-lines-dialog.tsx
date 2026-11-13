import type { BatchDebt } from "@/domains/fee-payment/types";
import { formatNumber } from "@/shared/lib/format";
import { Button } from "@/shared/ui/button";
import { Checkbox } from "@/shared/ui/checkbox";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/shared/ui/dialog";

/**
 * "Sửa chi tiết": which khoản thu this receipt takes. Money is never part-paid, so a line is
 * either collected for everything it still owes or left out of this receipt.
 */
export function EditLinesDialog({
  open,
  batches,
  excluded,
  onChange,
  onClose,
}: {
  open: boolean;
  batches: BatchDebt[];
  excluded: Set<number>;
  onChange: (next: Set<number>) => void;
  onClose: () => void;
}) {
  function toggle(id: number, on: boolean) {
    const next = new Set(excluded);
    if (on) next.delete(id);
    else next.add(id);
    onChange(next);
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Chọn khoản thu cho phiếu này</DialogTitle>
        </DialogHeader>
        <div className="max-h-[60vh] overflow-y-auto">
          {batches.map((batch) => (
            <div key={batch.batchId} className="mb-3">
              <p className="bg-muted text-muted-foreground px-3 py-1.5 text-sm font-semibold">{batch.batchName}</p>
              {batch.lines
                .filter((line) => line.outstanding > 0)
                .map((line) => (
                  <label
                    key={line.receivableId}
                    className="hover:bg-accent/50 flex cursor-pointer items-center gap-3 px-3 py-2 text-sm"
                  >
                    <Checkbox
                      checked={!excluded.has(line.receivableId)}
                      onCheckedChange={(value) => toggle(line.receivableId, value === true)}
                    />
                    <span className="min-w-0 flex-1 truncate">
                      {line.itemName}
                      {line.unitName ? ` (${line.unitName})` : ""}
                    </span>
                    <span className="text-muted-foreground tabular-nums">SL {line.quantity}</span>
                    <span className="w-24 text-right font-semibold tabular-nums">{formatNumber(line.outstanding)}</span>
                  </label>
                ))}
            </div>
          ))}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onChange(new Set())}>
            Chọn tất cả
          </Button>
          <Button type="button" onClick={onClose}>
            Xong
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
