import { useCreate, useList, useUpdate } from "@refinedev/core";
import { useState } from "react";
import { useForm, type Control, type FieldPath } from "react-hook-form";

import {
  GENDER_LABELS,
  type StructureGroup,
  type StudentFormValues,
  type StudentRow,
} from "@/domains/student/types";
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

const STEPS = [
  { title: "Thông tin học sinh", hint: "Thông tin cơ bản, lớp học" },
  { title: "Thông tin hộ khẩu", hint: "Địa chỉ, thông tin cư trú" },
  { title: "Thông tin phụ huynh", hint: "Liên hệ và tài khoản" },
] as const;

type Props = {
  /** The year the student is enrolled in, and the khối lớp and lớp it holds. */
  year: { id: number; name: string };
  groups: StructureGroup[];
  /** The row being edited, or null to add. */
  record: StudentRow | null;
  /** The class the list is showing, so adding from it starts in that class. */
  defaultClassId?: number;
  onClose: () => void;
  onSaved: () => void;
};

const text = (value: string | null | undefined) => value ?? "";

function initialValues(record: StudentRow | null, defaultClassId?: number): StudentFormValues {
  const s = record?.student;
  return {
    fullName: text(s?.fullName),
    studentCode: text(s?.studentCode),
    dateOfBirth: text(s?.dateOfBirth),
    gender: s?.gender ?? "",
    schoolClassId: record?.schoolClass
      ? String(record.schoolClass.id)
      : defaultClassId
        ? String(defaultClassId)
        : "",
    trainingSystemId: record?.trainingSystem ? String(record.trainingSystem.id) : "",
    personalId: text(s?.personalId),
    passportNumber: text(s?.passportNumber),
    statisticCode: text(s?.statisticCode),
    admissionYear: s?.admissionYear ? String(s.admissionYear) : String(new Date().getFullYear()),
    transferInDate: text(record?.transferInDate),
    healthInsuranceCode: text(s?.healthInsuranceCode),
    province: text(s?.province),
    district: text(s?.district),
    ward: text(s?.ward),
    area: text(s?.area),
    houseNumber: text(s?.houseNumber),
    hamlet: text(s?.hamlet),
    address: text(s?.address),
    guardianName: text(s?.guardianName),
    guardianPhone: text(s?.guardianPhone),
    guardianEmail: text(s?.guardianEmail),
    bankName: text(s?.bankName),
    bankAccountHolder: text(s?.bankAccountHolder),
    bankAccountNumber: text(s?.bankAccountNumber),
    needsInvoice: s?.needsInvoice ?? false,
  };
}

/** The text fields go as they are; the API reads a blank optional one as "clear it". */
function toPayload(values: StudentFormValues, adding: boolean) {
  const { schoolClassId, trainingSystemId, gender, admissionYear, dateOfBirth, transferInDate, ...rest } =
    values;
  return {
    ...rest,
    gender: gender || undefined,
    admissionYear: admissionYear ? Number(admissionYear) : undefined,
    dateOfBirth: dateOfBirth || undefined,
    transferInDate: transferInDate || undefined,
    schoolClass: { id: Number(schoolClassId) },
    // On add, no system is simply left out; on edit, `{id: null}` is how it is cleared.
    trainingSystem: trainingSystemId
      ? { id: Number(trainingSystemId) }
      : adding
        ? undefined
        : { id: null },
  };
}

/**
 * Add or edit a student, in three steps as the Jmix application did: the
 * student and their class, the address, the guardian. Nothing is sent until the
 * last step; the first step has to be valid before the others open, because the
 * class decides what the others mean.
 */
export function StudentFormDialog({ year, groups, record, defaultClassId, onClose, onSaved }: Props) {
  const adding = record === null;
  const [step, setStep] = useState(0);

  const form = useForm<StudentFormValues>({
    defaultValues: initialValues(record, defaultClassId),
  });

  const {
    result: { data: trainingSystems },
  } = useList<{ id: number; name: string }>({
    resource: "training_systems",
    pagination: { currentPage: 1, pageSize: 100 },
    sorters: [{ field: "name", order: "asc" }],
  });

  const { mutate: create, mutation: creating } = useCreate();
  const { mutate: update, mutation: updating } = useUpdate();
  const saving = creating.isPending || updating.isPending;

  async function goTo(target: number) {
    if (target > 0 && !(await form.trigger(["fullName", "schoolClassId"]))) {
      setStep(0);
      return;
    }
    setStep(target);
  }

  function submit(values: StudentFormValues) {
    const payload = toPayload(values, adding);
    const done = { onSuccess: () => { onSaved(); onClose(); } };
    if (record) {
      update({ resource: "students", id: record.id, values: payload }, done);
    } else {
      create(
        { resource: "students", values: { academicYear: { id: year.id }, ...payload } },
        done
      );
    }
  }

  // The record's own system is merged in: the list may not have arrived yet, and a
  // select cannot show a value whose option is missing.
  const systemOptions = (trainingSystems ?? []).map((s) => ({ value: String(s.id), label: s.name }));
  if (record?.trainingSystem && !systemOptions.some((o) => o.value === String(record.trainingSystem?.id))) {
    systemOptions.push({ value: String(record.trainingSystem.id), label: record.trainingSystem.name });
  }

  const control = form.control;

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="flex h-[min(44rem,calc(100dvh-2rem))] flex-col gap-0 overflow-hidden p-0 sm:max-w-4xl">
        <DialogHeader className="border-b px-6 py-4 pe-12">
          <DialogTitle>{adding ? "Thêm học sinh" : "Sửa học sinh"}</DialogTitle>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(submit)} className="flex min-h-0 flex-1 flex-col">
            <div className="flex min-h-0 flex-1">
              <ol className="bg-surface-subtle w-64 shrink-0 space-y-1 border-e p-3">
                {STEPS.map((item, index) => (
                  <li key={item.title}>
                    <button
                      type="button"
                      onClick={() => void goTo(index)}
                      className={cn(
                        "flex w-full items-center gap-3 rounded-md px-3 py-3 text-start",
                        step === index ? "bg-brand-subtle text-brand" : "hover:bg-accent/50"
                      )}
                    >
                      <span
                        className={cn(
                          "flex size-7 shrink-0 items-center justify-center rounded text-sm font-semibold",
                          step === index ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"
                        )}
                      >
                        {index + 1}
                      </span>
                      <span className="min-w-0">
                        <span className="block text-sm font-medium">{item.title}</span>
                        <span className="text-muted-foreground block text-xs">{item.hint}</span>
                      </span>
                    </button>
                  </li>
                ))}
              </ol>

              <div className="min-w-0 flex-1 overflow-y-auto p-6">
                <h2 className="mb-5 text-lg font-semibold">{STEPS[step].title}</h2>

                <div className={cn("grid items-start gap-x-4 gap-y-5 sm:grid-cols-3", step !== 0 && "hidden")}>
                  <Text control={control} name="fullName" label="Họ và tên" placeholder="Nhập họ và tên" required />
                  <Text control={control} name="studentCode" label="Mã HS / Mã thanh toán" placeholder="Tự sinh khi để trống" />
                  <Text control={control} name="dateOfBirth" label="Ngày sinh" type="date" />

                  <Pick
                    control={control}
                    name="gender"
                    label="Giới tính"
                    placeholder="Chọn giới tính"
                    options={Object.entries(GENDER_LABELS).map(([value, label]) => ({ value, label }))}
                  />
                  <Pick
                    control={control}
                    name="schoolClassId"
                    label="Lớp"
                    placeholder="Chọn lớp"
                    required
                    options={groups.flatMap((group) =>
                      group.classes.map((c) => ({ value: String(c.id), label: c.name }))
                    )}
                  />
                  <Pick
                    control={control}
                    name="trainingSystemId"
                    label="Hệ đào tạo"
                    placeholder="Chọn hệ đào tạo"
                    options={systemOptions}
                    clearable
                  />

                  <Text control={control} name="personalId" label="Số định danh cá nhân" placeholder="Nhập số định danh" />
                  <Text control={control} name="passportNumber" label="Số hộ chiếu" placeholder="Nhập số hộ chiếu" />
                  <Text control={control} name="statisticCode" label="Mã thống kê" placeholder="Nhập mã thống kê" />

                  <Text control={control} name="admissionYear" label="Năm nhập học" type="number" />
                  <Text control={control} name="transferInDate" label="Ngày chuyển đến" type="date" />
                  <Text control={control} name="healthInsuranceCode" label="Mã BHYT" placeholder="Nhập mã BHYT" />
                </div>

                <div className={cn("grid items-start gap-x-4 gap-y-5 sm:grid-cols-3", step !== 1 && "hidden")}>
                  <Text control={control} name="province" label="Tỉnh / Thành phố" />
                  <Text control={control} name="district" label="Quận / Huyện" />
                  <Text control={control} name="ward" label="Phường / Xã" />
                  <Text control={control} name="area" label="Khu vực" />
                  <Text control={control} name="houseNumber" label="Số nhà" />
                  <Text control={control} name="hamlet" label="Thôn / Xóm" />
                  <Text control={control} name="address" label="Địa chỉ hiện tại" className="sm:col-span-3" />
                </div>

                <div className={cn("grid items-start gap-x-4 gap-y-5 sm:grid-cols-3", step !== 2 && "hidden")}>
                  <Text control={control} name="guardianName" label="Họ tên phụ huynh" />
                  <Text control={control} name="guardianPhone" label="Số điện thoại" />
                  <Text control={control} name="guardianEmail" label="Email" type="email" />
                  <Text control={control} name="bankName" label="Tên ngân hàng" />
                  <Text control={control} name="bankAccountHolder" label="Chủ tài khoản" />
                  <Text control={control} name="bankAccountNumber" label="Số tài khoản" />
                  <FormField
                    control={control}
                    name="needsInvoice"
                    render={({ field }) => (
                      <FormItem className="flex items-center gap-2 sm:col-span-3">
                        <FormControl>
                          <Checkbox checked={field.value} onCheckedChange={(v) => field.onChange(v === true)} />
                        </FormControl>
                        <FormLabel className="m-0">Có nhu cầu nhận hóa đơn</FormLabel>
                      </FormItem>
                    )}
                  />
                </div>
              </div>
            </div>

            <DialogFooter className="border-t px-6 py-4 sm:justify-between">
              <Button type="button" variant="outline" onClick={onClose}>
                Hủy
              </Button>
              <div className="flex gap-2">
                {step > 0 && (
                  <Button type="button" variant="outline" onClick={() => void goTo(step - 1)}>
                    Quay lại
                  </Button>
                )}
                {step < STEPS.length - 1 ? (
                  <Button key="next" type="button" onClick={() => void goTo(step + 1)}>
                    Tiếp tục
                  </Button>
                ) : (
                  <Button key="save" type="submit" disabled={saving}>
                    {saving ? "Đang lưu…" : "Lưu học sinh"}
                  </Button>
                )}
              </div>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

type FieldName = FieldPath<StudentFormValues>;

function Text({
  control,
  name,
  label,
  placeholder,
  type,
  required,
  className,
}: {
  control: Control<StudentFormValues>;
  name: FieldName;
  label: string;
  placeholder?: string;
  type?: string;
  required?: boolean;
  className?: string;
}) {
  return (
    <FormField
      control={control}
      name={name}
      rules={required ? { validate: (v) => (String(v).trim() ? true : `Vui lòng nhập ${label.toLowerCase()}`) } : undefined}
      render={({ field }) => (
        <FormItem className={className}>
          <FormLabel>
            {label}
            {required && <span className="text-destructive ms-1">*</span>}
          </FormLabel>
          <FormControl>
            <Input
              {...field}
              value={typeof field.value === "boolean" ? "" : (field.value ?? "")}
              type={type}
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
  clearable,
}: {
  control: Control<StudentFormValues>;
  name: FieldName;
  label: string;
  placeholder: string;
  options: { value: string; label: string }[];
  required?: boolean;
  clearable?: boolean;
}) {
  return (
    <FormField
      control={control}
      name={name}
      rules={required ? { validate: (v) => (v ? true : `Vui lòng chọn ${label.toLowerCase()}`) } : undefined}
      render={({ field }) => (
        <FormItem>
          <FormLabel>
            {label}
            {required && <span className="text-destructive ms-1">*</span>}
          </FormLabel>
          <Select
            onValueChange={(value) => field.onChange(value === "__none" ? "" : value)}
            value={typeof field.value === "string" ? field.value : ""}
          >
            <FormControl>
              <SelectTrigger className="w-full">
                <SelectValue placeholder={placeholder} />
              </SelectTrigger>
            </FormControl>
            <SelectContent>
              {clearable && <SelectItem value="__none">Không chọn</SelectItem>}
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
