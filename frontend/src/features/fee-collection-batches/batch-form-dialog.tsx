import { useCreate, useList } from "@refinedev/core";
import { useEffect, useMemo, useRef, useState } from "react";

import type { AcademicYear } from "@/domains/academic-year/types";
import type { SchoolClass } from "@/domains/class-group/types";
import {
  BATCH_SCOPE_LABELS,
  defaultBatchName,
  SCHOOL_MONTHS,
  type FeeCollectionBatch,
  type FeeCollectionScope,
} from "@/domains/fee-collection-batch/types";
import { Button } from "@/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/shared/ui/dialog";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";
import { BatchItemPicker, PreviewStrip, rowFromCandidate } from "./batch-item-picker";
import { Segmented } from "./segmented";
import { useBatchPreview } from "./use-batch-preview";

type Named = { id: number; name: string };

type Props = {
  year: AcademicYear;
  onClose: () => void;
  /** Called with the new đợt once the API has made it; the caller decides where to go. */
  onCreated: (batch: FeeCollectionBatch) => void;
};

const SCOPE_OPTIONS = (Object.keys(BATCH_SCOPE_LABELS) as FeeCollectionScope[]).map((value) => ({
  value,
  label: BATCH_SCOPE_LABELS[value],
}));

/**
 * Making a đợt thu: a name, a month and whom it is for, then the khoản thu to collect.
 *
 * The khoản thu on offer depend on the first three — only those due in the month and
 * reaching the scope — so the dialog asks the API what a đợt like this would be as the
 * choices change, and shows who it reaches and what it would come to before anything is
 * made. Mandatory khoản thu start ticked: nobody chooses to be charged for them, and
 * leaving one out should be a decision.
 */
export function BatchFormDialog({ year, onClose, onCreated }: Props) {
  const [month, setMonth] = useState<number>(() => new Date().getMonth() + 1);
  const [name, setName] = useState(() => defaultBatchName(new Date().getMonth() + 1, year.name));
  const [nameTouched, setNameTouched] = useState(false);
  const [scope, setScope] = useState<FeeCollectionScope>("ALL_SCHOOL");
  const [classGroupId, setClassGroupId] = useState<number | null>(null);
  const [schoolClassId, setSchoolClassId] = useState<number | null>(null);
  /** The khoản thu ticked, as khoản thu id to quantity. */
  const [selected, setSelected] = useState<Record<number, number>>({});

  const {
    result: { data: groups },
  } = useList<Named>({
    resource: "class_groups",
    pagination: { currentPage: 1, pageSize: 200 },
    filters: [{ field: "academicYear.id", operator: "eq", value: year.id }],
    sorters: [{ field: "name", order: "asc" }],
  });
  const {
    result: { data: allClasses },
  } = useList<SchoolClass>({
    resource: "school_classes",
    pagination: { currentPage: 1, pageSize: 1000 },
    sorters: [{ field: "name", order: "asc" }],
  });
  const classes = useMemo(() => {
    const groupIds = new Set((groups ?? []).map((g) => g.id));
    return (allClasses ?? []).filter((c) => groupIds.has(c.classGroup.id));
  }, [allClasses, groups]);

  const lines = useMemo(
    () => Object.entries(selected).map(([id, quantity]) => ({ feeItemId: Number(id), quantity })),
    [selected]
  );
  const { preview, pending, isPlaceholder, ready } = useBatchPreview({
    yearId: year.id,
    month,
    scope,
    classGroupId,
    schoolClassId,
    lines,
  });

  // Whenever the question changes (month or whom), the answer is a different list of khoản thu:
  // start from the mandatory ones. The key is what the list depends on and nothing else.
  const targetKey = `${month}|${scope}|${classGroupId ?? ""}|${schoolClassId ?? ""}`;
  const appliedKey = useRef<string | null>(null);
  useEffect(() => {
    if (!preview || isPlaceholder || appliedKey.current === targetKey) return;
    appliedKey.current = targetKey;
    const next: Record<number, number> = {};
    for (const candidate of preview.candidates) {
      if (candidate.mandatory) next[candidate.feeItemId] = 1;
    }
    setSelected(next);
  }, [preview, isPlaceholder, targetKey]);

  const rows = useMemo(() => (preview?.candidates ?? []).map(rowFromCandidate), [preview]);

  const { mutate: create, mutation } = useCreate<FeeCollectionBatch>();

  const targetChosen =
    scope === "ALL_SCHOOL" ||
    (scope === "CLASS_GROUP" && classGroupId !== null) ||
    (scope === "CLASS" && schoolClassId !== null);
  const noStudents = ready && !pending && preview !== undefined && preview.studentCount === 0;
  const canSubmit =
    name.trim() !== "" &&
    targetChosen &&
    lines.length > 0 &&
    !pending &&
    (preview?.problems.length ?? 0) === 0 &&
    !noStudents &&
    !mutation.isPending;

  function changeMonth(next: number) {
    setMonth(next);
    if (!nameTouched) setName(defaultBatchName(next, year.name));
  }

  function changeScope(next: FeeCollectionScope) {
    setScope(next);
    setClassGroupId(null);
    setSchoolClassId(null);
  }

  function submit() {
    if (!canSubmit) return;
    create(
      {
        resource: "fee_collection_batches",
        values: {
          academicYear: { id: year.id },
          name: name.trim(),
          collectionMonth: month,
          scope,
          scopeClassGroup: scope === "CLASS_GROUP" && classGroupId ? { id: classGroupId } : null,
          scopeClass: scope === "CLASS" && schoolClassId ? { id: schoolClassId } : null,
          items: lines,
        },
        successNotification: () => ({ type: "success", message: "Đã lập đợt thu" }),
      },
      {
        onSuccess: (response) => {
          onCreated(response.data);
          onClose();
        },
      }
    );
  }

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] flex-col gap-0 overflow-hidden p-0 sm:max-w-3xl">
        <DialogHeader className="border-b px-6 py-4 pe-12">
          <DialogTitle>Lập đợt thu</DialogTitle>
        </DialogHeader>

        <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-5">
          <div className="grid gap-4 sm:grid-cols-[1fr_11rem]">
            <div className="space-y-1.5">
              <Label htmlFor="batch-name">
                Tên đợt thu<span className="text-destructive ms-1">*</span>
              </Label>
              <Input
                id="batch-name"
                value={name}
                onChange={(event) => {
                  setName(event.target.value);
                  setNameTouched(true);
                }}
                placeholder="Ví dụ: Đợt thu tháng 10/2026"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="batch-month">
                Tháng thu<span className="text-destructive ms-1">*</span>
              </Label>
              <Select value={String(month)} onValueChange={(value) => changeMonth(Number(value))}>
                <SelectTrigger id="batch-month" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SCHOOL_MONTHS.map((m) => (
                    <SelectItem key={m} value={String(m)}>
                      Tháng {m}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>
              Phạm vi<span className="text-destructive ms-1">*</span>
            </Label>
            <div className="flex flex-wrap items-center gap-3">
              <Segmented label="Phạm vi" value={scope} onChange={changeScope} options={SCOPE_OPTIONS} />
              {scope === "CLASS_GROUP" && (
                <Select
                  value={classGroupId === null ? "" : String(classGroupId)}
                  onValueChange={(value) => setClassGroupId(Number(value))}
                >
                  <SelectTrigger className="w-48" aria-label="Khối">
                    <SelectValue placeholder="Chọn khối" />
                  </SelectTrigger>
                  <SelectContent>
                    {(groups ?? []).map((g) => (
                      <SelectItem key={g.id} value={String(g.id)}>
                        {g.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              {scope === "CLASS" && (
                <Select
                  value={schoolClassId === null ? "" : String(schoolClassId)}
                  onValueChange={(value) => setSchoolClassId(Number(value))}
                >
                  <SelectTrigger className="w-48" aria-label="Lớp">
                    <SelectValue placeholder="Chọn lớp" />
                  </SelectTrigger>
                  <SelectContent>
                    {classes.map((c) => (
                      <SelectItem key={c.id} value={String(c.id)}>
                        {c.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
          </div>

          <div className="space-y-2">
            <div>
              <h3 className="text-sm font-semibold">Khoản thu trong đợt</h3>
              <p className="text-muted-foreground text-xs">
                Chỉ hiện khoản thu đến hạn trong tháng {month} và áp dụng cho phạm vi này. Khoản tự chọn chỉ
                tính cho học sinh đã đăng ký.
              </p>
            </div>
            {targetChosen ? (
              <BatchItemPicker
                rows={rows}
                selected={selected}
                empty={
                  noStudents
                    ? "Phạm vi này chưa có học sinh nào được xếp lớp."
                    : `Không có khoản thu nào đến hạn trong tháng ${month} cho phạm vi này.`
                }
                onToggle={(feeItemId, on) =>
                  setSelected((current) => {
                    const next = { ...current };
                    if (on) next[feeItemId] = 1;
                    else delete next[feeItemId];
                    return next;
                  })
                }
                onQuantity={(feeItemId, quantity) =>
                  setSelected((current) => ({ ...current, [feeItemId]: quantity }))
                }
              />
            ) : (
              <div className="text-muted-foreground rounded-lg border border-dashed p-6 text-center text-sm">
                {scope === "CLASS_GROUP" ? "Chọn khối để xem các khoản thu." : "Chọn lớp để xem các khoản thu."}
              </div>
            )}
          </div>

          <PreviewStrip preview={preview} pending={pending} />
        </div>

        <DialogFooter className="border-t px-6 py-4">
          <Button type="button" variant="outline" onClick={onClose}>
            Hủy
          </Button>
          <Button type="button" disabled={!canSubmit} onClick={submit}>
            Lập đợt thu
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
