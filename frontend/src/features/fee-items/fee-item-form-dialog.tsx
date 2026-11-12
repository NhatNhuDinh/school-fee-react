import { useCreate, useList, useUpdate } from "@refinedev/core";
import { Plus, Trash2 } from "lucide-react";
import { useMemo } from "react";
import { useFieldArray, useForm, type Control, type FieldPath } from "react-hook-form";

import type { AcademicYear } from "@/domains/academic-year/types";
import type { SchoolClass } from "@/domains/class-group/types";
import {
  FEE_CYCLE_LABELS,
  FEE_PRICE_MODE_LABELS,
  FEE_SCOPE_LABELS,
  scheduleTemplates,
  type FeeCollectionCycle,
  type FeeItem,
  type FeeItemFormValues,
} from "@/domains/fee-item/types";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Checkbox } from "@/shared/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/shared/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/shared/ui/form";
import { Input } from "@/shared/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";

type Named = { id: number; name: string };

type Props = {
  /** The year the khoản thu belongs to; it also decides the collection months. */
  year: AcademicYear;
  /** The row being edited, or null to add. */
  record: FeeItem | null;
  onClose: () => void;
  onSaved: () => void;
};

const text = (value: string | number | null | undefined) =>
  value === null || value === undefined ? "" : String(value);

function initialValues(record: FeeItem | null, year: AcademicYear): FeeItemFormValues {
  const cycle: FeeCollectionCycle = record?.collectionCycle ?? "MONTHLY";
  const months: string[] = [];
  for (const template of scheduleTemplates(cycle, year)) {
    const saved = record?.collectionSchedules.find((s) => s.sequence === template.sequence);
    months[template.sequence - 1] = String(saved?.collectionMonth ?? template.defaultMonth);
  }
  return {
    name: record?.name ?? "",
    scope: record?.scope ?? "ALL_SCHOOL",
    scopeClassGroupId: text(record?.scopeClassGroup?.id),
    scopeClassId: text(record?.scopeClass?.id),
    priceMode: record?.priceMode ?? "SINGLE",
    basePrice: text(record?.basePrice),
    measurementUnitId: text(record?.measurementUnit.id),
    vatRate: text(record?.vatRate ?? 0),
    collectionCycle: cycle,
    months,
    discountEligible: record?.discountEligible ?? false,
    mandatory: record?.mandatory ?? false,
    serviceFee: record?.serviceFee ?? false,
    refundable: record?.refundable ?? false,
    invoiceEnabled: record?.invoiceEnabled ?? false,
    receiptEnabled: record?.receiptEnabled ?? false,
    internalCollection: record?.internalCollection ?? false,
    invoiceRate: text(record?.invoiceRate ?? 100),
    active: record?.active ?? true,
    prices:
      record?.prices.map((p) => ({
        classGroupId: String(p.classGroup.id),
        areaName: p.areaName ?? "",
        amount: text(p.amount),
      })) ?? [],
  };
}

/**
 * What goes to the API. Only what the chosen scope, price mode and cycle use is
 * sent; the API clears the rest regardless, so this is a courtesy rather than the
 * rule.
 */
function toPayload(values: FeeItemFormValues, year: AcademicYear) {
  const templates = scheduleTemplates(values.collectionCycle, year);
  return {
    name: values.name.trim(),
    scope: values.scope,
    scopeClassGroup: values.scope === "CLASS_GROUP" ? { id: Number(values.scopeClassGroupId) } : undefined,
    scopeClass: values.scope === "CLASS" ? { id: Number(values.scopeClassId) } : undefined,
    priceMode: values.priceMode,
    basePrice: values.priceMode === "SINGLE" ? Number(values.basePrice) : undefined,
    measurementUnit: { id: Number(values.measurementUnitId) },
    vatRate: Number(values.vatRate || 0),
    collectionCycle: values.collectionCycle,
    discountEligible: values.discountEligible,
    mandatory: values.mandatory,
    serviceFee: values.serviceFee,
    refundable: values.refundable,
    invoiceEnabled: values.invoiceEnabled,
    receiptEnabled: values.receiptEnabled,
    internalCollection: values.internalCollection,
    invoiceRate: Number(values.invoiceRate || 0),
    active: values.active,
    prices:
      values.priceMode === "MULTIPLE"
        ? values.prices.map((p) => ({
            classGroup: { id: Number(p.classGroupId) },
            areaName: p.areaName.trim() || undefined,
            amount: Number(p.amount),
          }))
        : [],
    collectionSchedules: templates.map((t) => ({
      sequence: t.sequence,
      collectionMonth: Number(values.months[t.sequence - 1] ?? t.defaultMonth),
    })),
  };
}

/**
 * Add or edit a khoản thu, in four numbered sections as the Jmix application had
 * it: what it is, its price, when it is collected, and the rules that apply.
 * Everything is on one scrolling page rather than behind steps, because the
 * sections depend on one another — the price list needs the khối lớp, the dates
 * need the cycle — and hiding one behind a step hides the dependency.
 */
export function FeeItemFormDialog({ year, record, onClose, onSaved }: Props) {
  const adding = record === null;

  const form = useForm<FeeItemFormValues>({ defaultValues: initialValues(record, year) });
  const { control, watch, setValue } = form;
  const prices = useFieldArray({ control, name: "prices" });

  const scope = watch("scope");
  const priceMode = watch("priceMode");
  const cycle = watch("collectionCycle");
  const invoiceEnabled = watch("invoiceEnabled");

  // ---- the lists the selects are made of
  const {
    result: { data: units },
  } = useList<Named>({
    resource: "measurement_units",
    pagination: { currentPage: 1, pageSize: 200 },
    sorters: [{ field: "name", order: "asc" }],
  });
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

  const groupOptions = useMemo(
    () =>
      withCurrent(
        (groups ?? []).map((g) => ({ value: String(g.id), label: g.name })),
        record?.scopeClassGroup,
        ...(record?.prices.map((p) => p.classGroup) ?? [])
      ),
    [groups, record]
  );
  const classOptions = useMemo(() => {
    const groupIds = new Set((groups ?? []).map((g) => g.id));
    return withCurrent(
      (allClasses ?? [])
        .filter((c) => groupIds.has(c.classGroup.id))
        .map((c) => ({ value: String(c.id), label: c.name })),
      record?.scopeClass
    );
  }, [allClasses, groups, record]);
  const unitOptions = useMemo(
    () =>
      withCurrent(
        (units ?? []).map((u) => ({ value: String(u.id), label: u.name })),
        record?.measurementUnit
      ),
    [units, record]
  );

  // ---- the collection dates follow the cycle
  const templates = useMemo(() => scheduleTemplates(cycle, year), [cycle, year]);

  /**
   * A cycle just chosen has no months yet, so each collection gets its first
   * allowed one — set here, in the handler that changes the cycle, rather than in
   * an effect that would have to notice the change afterwards: the selects for the
   * new collections mount on this render and must already find a value in the form.
   */
  function onCycleChange(next: string) {
    const fresh = scheduleTemplates(next as FeeCollectionCycle, year);
    setValue(
      "months",
      fresh.map((template) => String(template.defaultMonth))
    );
  }

  const { mutate: create, mutation: creating } = useCreate();
  const { mutate: update, mutation: updating } = useUpdate();
  const saving = creating.isPending || updating.isPending;

  function submit(values: FeeItemFormValues) {
    if (!validate(values)) return;
    const payload = toPayload(values, year);
    const done = {
      onSuccess: () => {
        onSaved();
        onClose();
      },
    };
    if (record) {
      update({ resource: "fee_items", id: record.id, values: payload }, done);
    } else {
      create({ resource: "fee_items", values: { academicYear: { id: year.id }, ...payload } }, done);
    }
  }

  /** The rules the form can check itself; the API checks the same ones again. */
  function validate(values: FeeItemFormValues): boolean {
    let valid = true;
    const fail = (name: FieldPath<FeeItemFormValues>, message: string) => {
      form.setError(name, { type: "validate", message });
      valid = false;
    };
    if (!values.name.trim()) fail("name", "Vui lòng nhập tên khoản thu");
    if (values.scope === "CLASS_GROUP" && !values.scopeClassGroupId) {
      fail("scopeClassGroupId", "Vui lòng chọn khối áp dụng");
    }
    if (values.scope === "CLASS" && !values.scopeClassId) {
      fail("scopeClassId", "Vui lòng chọn lớp áp dụng");
    }
    if (!values.measurementUnitId) fail("measurementUnitId", "Vui lòng chọn đơn vị tính");
    if (values.priceMode === "SINGLE" && values.basePrice === "") {
      fail("basePrice", "Vui lòng nhập đơn giá cho khoản thu");
    }
    if (values.priceMode === "MULTIPLE") {
      if (values.prices.length === 0) {
        fail("priceMode", "Vui lòng khai báo ít nhất một đơn giá");
      }
      values.prices.forEach((p, index) => {
        if (!p.classGroupId) fail(`prices.${index}.classGroupId`, "Chọn khối");
        if (p.amount === "") fail(`prices.${index}.amount`, "Nhập đơn giá");
      });
    }
    const vat = Number(values.vatRate || 0);
    if (vat < 0 || vat > 100) fail("vatRate", "Thuế suất từ 0 đến 100%");
    if (values.collectionCycle === "SEMESTER" && templates.length === 0) {
      fail("collectionCycle", "Năm học chưa có giai đoạn để thiết lập kỳ thu học kỳ");
    }
    return valid;
  }

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="flex h-[min(46rem,calc(100dvh-2rem))] flex-col gap-0 overflow-hidden p-0 sm:max-w-4xl">
        <DialogHeader className="border-b px-6 py-4 pe-12">
          <DialogTitle>{adding ? "Thêm khoản thu" : "Sửa khoản thu"}</DialogTitle>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(submit)} className="flex min-h-0 flex-1 flex-col">
            <div className="min-h-0 flex-1 space-y-7 overflow-y-auto px-6 py-6">
              {/* 01 — what it is */}
              <Section number="01" title="Thông tin khoản thu">
                <div className="grid gap-4 sm:grid-cols-3">
                  <Text control={control} name="name" label="Tên khoản thu" placeholder="Nhập tên khoản thu" required />
                  <Pick
                    control={control}
                    name="scope"
                    label="Phạm vi thu"
                    required
                    options={Object.entries(FEE_SCOPE_LABELS).map(([value, label]) => ({ value, label }))}
                    onChange={(value) => {
                      if (value !== "CLASS_GROUP") setValue("scopeClassGroupId", "");
                      if (value !== "CLASS") setValue("scopeClassId", "");
                    }}
                  />
                  {scope === "CLASS_GROUP" && (
                    <Pick
                      control={control}
                      name="scopeClassGroupId"
                      label="Khối áp dụng"
                      placeholder="Chọn khối"
                      required
                      options={groupOptions}
                    />
                  )}
                  {scope === "CLASS" && (
                    <Pick
                      control={control}
                      name="scopeClassId"
                      label="Lớp áp dụng"
                      placeholder="Chọn lớp"
                      required
                      options={classOptions}
                    />
                  )}
                </div>
              </Section>

              {/* 02 — the price */}
              <Section number="02" title="Đơn giá">
                <div className="grid gap-4 sm:grid-cols-3">
                  <Pick
                    control={control}
                    name="priceMode"
                    label="Cách xác định đơn giá"
                    options={Object.entries(FEE_PRICE_MODE_LABELS).map(([value, label]) => ({ value, label }))}
                  />
                  <Text control={control} name="vatRate" label="Thuế suất GTGT (%)" type="number" />
                  <Pick
                    control={control}
                    name="measurementUnitId"
                    label="Đơn vị tính"
                    placeholder="Chọn đơn vị tính"
                    required
                    options={unitOptions}
                  />
                  {priceMode === "SINGLE" && (
                    <Text
                      control={control}
                      name="basePrice"
                      label="Đơn giá đã bao gồm thuế GTGT"
                      type="number"
                      required
                    />
                  )}
                </div>

                {priceMode === "MULTIPLE" && (
                  <div className="space-y-2.5">
                    <div className="flex items-center justify-between">
                      <h4 className="text-sm font-semibold">Bảng giá</h4>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => prices.append({ classGroupId: "", areaName: "", amount: "" })}
                      >
                        <Plus />
                        Thêm dòng
                      </Button>
                    </div>
                    <div className="overflow-hidden rounded-lg border">
                      <div className="text-overline text-muted-foreground bg-surface-subtle grid grid-cols-[1fr_1fr_1fr_3rem] gap-3 px-3.5 py-2">
                        <span>Khối</span>
                        <span>Khu vực</span>
                        <span>Đơn giá đã gồm GTGT</span>
                        <span />
                      </div>
                      {prices.fields.length === 0 && (
                        <p className="text-muted-foreground px-3.5 py-6 text-center text-sm">
                          Chưa có đơn giá nào. Bấm "Thêm dòng" để khai báo.
                        </p>
                      )}
                      {prices.fields.map((field, index) => (
                        <div
                          key={field.id}
                          className="grid grid-cols-[1fr_1fr_1fr_3rem] items-start gap-3 border-t px-3.5 py-2.5"
                        >
                          <Pick
                            control={control}
                            name={`prices.${index}.classGroupId`}
                            label="Khối"
                            hideLabel
                            placeholder="Chọn khối"
                            options={groupOptions}
                          />
                          <Text
                            control={control}
                            name={`prices.${index}.areaName`}
                            label="Khu vực"
                            hideLabel
                            placeholder="Không bắt buộc"
                          />
                          <Text
                            control={control}
                            name={`prices.${index}.amount`}
                            label="Đơn giá"
                            hideLabel
                            type="number"
                          />
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-sm"
                            aria-label="Xóa dòng"
                            className="text-destructive"
                            onClick={() => prices.remove(index)}
                          >
                            <Trash2 />
                          </Button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </Section>

              {/* 03 — when it is collected */}
              <Section number="03" title="Kỳ thu và thời điểm thu">
                <div className="grid gap-4 sm:grid-cols-4">
                  <Pick
                    control={control}
                    name="collectionCycle"
                    label="Kỳ thu"
                    options={Object.entries(FEE_CYCLE_LABELS).map(([value, label]) => ({ value, label }))}
                    onChange={onCycleChange}
                    className="sm:col-span-2"
                  />
                  {cycle === "MONTHLY" && (
                    <p className="text-muted-foreground self-end pb-2 text-sm sm:col-span-2">
                      Sẽ tự động thu hàng tháng.
                    </p>
                  )}
                </div>

                {cycle === "SEMESTER" && templates.length === 0 && (
                  <p className="text-warning-text bg-warning-subtle rounded-md px-3 py-2 text-sm">
                    Năm học chưa có giai đoạn. Hãy khai báo giai đoạn trước khi chọn kỳ thu theo học kỳ.
                  </p>
                )}

                {templates.length > 0 && (
                  <div className={cn("grid gap-4", templates.length === 1 ? "sm:grid-cols-2" : "sm:grid-cols-4")}>
                    {templates.map((template) => (
                      <Pick
                        key={`${cycle}-${template.sequence}`}
                        control={control}
                        name={`months.${template.sequence - 1}`}
                        label={`Thời điểm thu ${template.label}`}
                        options={template.months.map((month) => ({
                          value: String(month),
                          label: `Tháng ${month}`,
                        }))}
                      />
                    ))}
                  </div>
                )}
              </Section>

              {/* 04 — the rules */}
              <Section number="04" title="Quy tắc áp dụng">
                <div className="grid gap-x-4 gap-y-3 sm:grid-cols-4">
                  <Check control={control} name="discountEligible" label="Áp dụng miễn giảm" />
                  <Check control={control} name="internalCollection" label="Thu nội bộ" />
                  <Check control={control} name="mandatory" label="Khoản thu bắt buộc" />
                  <Check control={control} name="active" label="Theo dõi" />
                  <Check control={control} name="serviceFee" label="Khoản thu dịch vụ" />
                  <Check control={control} name="refundable" label="Cho phép hoàn trả" />
                  <Check control={control} name="invoiceEnabled" label="Cho phép xuất hóa đơn" />
                  <Check control={control} name="receiptEnabled" label="Cho phép xuất chứng từ" />
                </div>
                {invoiceEnabled && (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Text
                      control={control}
                      name="invoiceRate"
                      label="Tỷ lệ số tiền xuất hóa đơn (%)"
                      type="number"
                    />
                  </div>
                )}
              </Section>
            </div>

            <DialogFooter className="border-t px-6 py-4">
              <Button type="button" variant="outline" onClick={onClose}>
                Hủy
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? "Đang lưu…" : "Lưu"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

// ---- small pieces

/** The options plus the record's own values, which the list may not have delivered yet. */
function withCurrent(
  options: { value: string; label: string }[],
  ...current: (Named | null | undefined)[]
) {
  const merged = [...options];
  for (const item of current) {
    if (item && !merged.some((o) => o.value === String(item.id))) {
      merged.push({ value: String(item.id), label: item.name });
    }
  }
  return merged;
}

function Section({ number, title, children }: { number: string; title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-4">
      <div className="flex items-center gap-2.5">
        <span className="bg-primary/10 text-primary flex size-7 items-center justify-center rounded-md text-xs font-bold">
          {number}
        </span>
        <h3 className="text-[15px] font-semibold">{title}</h3>
      </div>
      {children}
    </section>
  );
}

type FieldName = FieldPath<FeeItemFormValues>;

function Text({
  control,
  name,
  label,
  placeholder,
  type,
  required,
  hideLabel,
  className,
}: {
  control: Control<FeeItemFormValues>;
  name: FieldName;
  label: string;
  placeholder?: string;
  type?: string;
  required?: boolean;
  hideLabel?: boolean;
  className?: string;
}) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem className={className}>
          <FormLabel className={hideLabel ? "sr-only" : undefined}>
            {label}
            {required && <span className="text-destructive ms-1">*</span>}
          </FormLabel>
          <FormControl>
            <Input
              {...field}
              value={typeof field.value === "string" || typeof field.value === "number" ? field.value : ""}
              type={type}
              min={type === "number" ? 0 : undefined}
              inputMode={type === "number" ? "decimal" : undefined}
              placeholder={placeholder}
            />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}

function Pick({
  control,
  name,
  label,
  placeholder,
  options,
  required,
  hideLabel,
  className,
  onChange,
}: {
  control: Control<FeeItemFormValues>;
  name: FieldName;
  label: string;
  placeholder?: string;
  options: { value: string; label: string }[];
  required?: boolean;
  hideLabel?: boolean;
  className?: string;
  onChange?: (value: string) => void;
}) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem className={className}>
          <FormLabel className={hideLabel ? "sr-only" : undefined}>
            {label}
            {required && <span className="text-destructive ms-1">*</span>}
          </FormLabel>
          <Select
            value={typeof field.value === "string" ? field.value : ""}
            onValueChange={(value) => {
              field.onChange(value);
              onChange?.(value);
            }}
          >
            <FormControl>
              <SelectTrigger className="w-full">
                <SelectValue placeholder={placeholder} />
              </SelectTrigger>
            </FormControl>
            <SelectContent>
              {options.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}

function Check({
  control,
  name,
  label,
}: {
  control: Control<FeeItemFormValues>;
  name: FieldName;
  label: string;
}) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem className="flex items-center gap-2">
          <FormControl>
            <Checkbox checked={field.value === true} onCheckedChange={(value) => field.onChange(value === true)} />
          </FormControl>
          <FormLabel className="m-0 font-normal">{label}</FormLabel>
        </FormItem>
      )}
    />
  );
}
