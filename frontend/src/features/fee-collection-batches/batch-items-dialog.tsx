import { useCustom, useCustomMutation } from "@refinedev/core";
import { useEffect, useMemo, useRef, useState } from "react";

import {
  type FeeCollectionBatch,
  type FeeCollectionBatchItem,
} from "@/domains/fee-collection-batch/types";
import { API_URL } from "@/shared/api/constants";
import { customResult } from "@/shared/hooks/use-custom-result";
import { formatCurrency } from "@/shared/lib/format";
import { Button } from "@/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/shared/ui/dialog";
import { BatchItemPicker, PreviewStrip, rowFromCandidate, type PickerRow } from "./batch-item-picker";
import { useBatchPreview } from "./use-batch-preview";

type Props = {
  batch: FeeCollectionBatch;
  onClose: () => void;
  onSaved: () => void;
};

/**
 * Changing the khoản thu of a đợt thu that exists. The name, month and scope are fixed once a
 * đợt is made, so this is only the picker — the same one the create dialog uses.
 *
 * What the API refuses it says in the row rather than after the click: a khoản thu money has
 * been collected for cannot be unticked, and a new quantity may not bring what a student owes
 * below what they have paid (the API checks that one and answers with the count of students).
 * A khoản thu already in the đợt that no longer reaches anyone — nobody is registered any more
 * — is still listed, because it is in the đợt, and unticking it is how it leaves.
 */
export function BatchItemsDialog({ batch, onClose, onSaved }: Props) {
  const { result, query } = useCustom<FeeCollectionBatchItem[]>({
    url: `${API_URL}/fee_collection_batches/${batch.id}/items`,
    method: "get",
  });
  const current = customResult(result?.data as unknown as { length: number } | undefined, "length") as
    | FeeCollectionBatchItem[]
    | undefined;

  const [selected, setSelected] = useState<Record<number, number>>({});
  const initialised = useRef(false);
  useEffect(() => {
    if (!current || initialised.current) return;
    initialised.current = true;
    setSelected(Object.fromEntries(current.map((item) => [item.feeItemId, item.quantity])));
  }, [current]);

  const lines = useMemo(
    () => Object.entries(selected).map(([id, quantity]) => ({ feeItemId: Number(id), quantity })),
    [selected]
  );
  const { preview, pending } = useBatchPreview(
    {
      yearId: batch.academicYear.id,
      month: batch.collectionMonth,
      scope: batch.scope,
      classGroupId: batch.scopeClassGroup?.id ?? null,
      schoolClassId: batch.scopeClass?.id ?? null,
      lines,
    },
    initialised.current
  );

  const rows = useMemo<PickerRow[]>(() => {
    const byId = new Map<number, PickerRow>();
    for (const candidate of preview?.candidates ?? []) {
      byId.set(candidate.feeItemId, rowFromCandidate(candidate));
    }
    for (const item of current ?? []) {
      const known = byId.get(item.feeItemId);
      const collected = item.collectedAmount > 0;
      const locked = collected
        ? `Đã thu ${formatCurrency(item.collectedAmount)} — không thể bỏ khỏi đợt`
        : undefined;
      if (known) {
        byId.set(item.feeItemId, { ...known, lockedReason: locked });
      } else {
        byId.set(item.feeItemId, {
          feeItemId: item.feeItemId,
          name: item.name,
          unitName: item.unitName,
          mandatory: item.mandatory,
          detail: `Đã có trong đợt · ${item.studentCount} học sinh`,
          minUnitPrice: item.minUnitPrice,
          maxUnitPrice: item.maxUnitPrice,
          studentsWithoutPrice: 0,
          quantityEditable: false,
          lockedReason: locked,
        });
      }
    }
    return [...byId.values()];
  }, [preview, current]);

  const { mutate, mutation } = useCustomMutation();
  const dirty = useMemo(() => {
    if (!current) return false;
    if (current.length !== lines.length) return true;
    return lines.some((line) => {
      const before = current.find((item) => item.feeItemId === line.feeItemId);
      return !before || before.quantity !== line.quantity;
    });
  }, [current, lines]);
  const canSave = dirty && lines.length > 0 && !pending && !mutation.isPending;

  function save() {
    if (!canSave) return;
    mutate(
      {
        url: `${API_URL}/fee_collection_batches/${batch.id}/items`,
        method: "put",
        config: { headers: { "Content-Type": "application/json" } },
        values: { items: lines },
        successNotification: () => ({ type: "success", message: "Đã cập nhật khoản thu của đợt" }),
      },
      {
        onSuccess: () => {
          onSaved();
          onClose();
        },
      }
    );
  }

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent
        aria-describedby={undefined}
        className="flex max-h-[calc(100dvh-2rem)] flex-col gap-0 overflow-hidden p-0 sm:max-w-3xl"
      >
        <DialogHeader className="border-b px-6 py-4 pe-12">
          <DialogTitle>Cập nhật khoản thu</DialogTitle>
        </DialogHeader>

        <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-5">
          {query.isLoading || !initialised.current ? (
            <div className="text-muted-foreground p-6 text-center text-sm">Đang tải…</div>
          ) : (
            <BatchItemPicker
              rows={rows}
              selected={selected}
              empty="Đợt thu này chưa có khoản thu nào."
              onToggle={(feeItemId, on) =>
                setSelected((existing) => {
                  const next = { ...existing };
                  if (on) next[feeItemId] = 1;
                  else delete next[feeItemId];
                  return next;
                })
              }
              onQuantity={(feeItemId, quantity) =>
                setSelected((existing) => ({ ...existing, [feeItemId]: quantity }))
              }
            />
          )}
          <PreviewStrip preview={preview} pending={pending} label="Phải thu theo đăng ký hiện tại" />
        </div>

        <DialogFooter className="border-t px-6 py-4">
          <Button type="button" variant="outline" onClick={onClose}>
            Hủy
          </Button>
          <Button type="button" disabled={!canSave} onClick={save}>
            Lưu thay đổi
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
