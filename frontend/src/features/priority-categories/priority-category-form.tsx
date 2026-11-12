import { useSelect } from "@refinedev/core";
import { useForm } from "@refinedev/react-hook-form";
import { useEffect } from "react";
import type { Control, FieldValues, UseFormReturn } from "react-hook-form";
import { useNavigate } from "react-router";

import {
  APPLICATION_PERIOD_LABELS,
  DISCOUNT_TYPE_LABELS,
  type PriorityCategory,
  type PriorityCategoryFormValues,
} from "@/domains/priority-category/types";
import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
import { FormActions, FormPanel } from "@/shared/components/form/form-panel";
import {
  CreateView,
  CreateViewHeader,
} from "@/shared/components/views/create-view";
import { EditView, EditViewHeader } from "@/shared/components/views/edit-view";
import { Button } from "@/shared/ui/button";
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

const DEFAULT_VALUES: PriorityCategoryFormValues = {
  academicYear: { id: "" },
  name: "",
  discountType: "PERCENT",
  discountValue: "0",
  applicationPeriod: "FULL_YEAR",
  fromMonth: "",
  toMonth: "",
};

const MONTHS = Array.from({ length: 12 }, (_, index) => String(index + 1));

function EnumSelect({
  control,
  name,
  label,
  options,
  className,
}: {
  control: Control<FieldValues>;
  name: string;
  label: string;
  options: Record<string, string>;
  className: string;
}) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem className={className}>
          <FormLabel>{label}</FormLabel>
          <Select onValueChange={field.onChange} value={field.value ?? ""}>
            <FormControl>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
            </FormControl>
            <SelectContent>
              {Object.entries(options).map(([value, text]) => (
                <SelectItem key={value} value={value}>
                  {text}
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

function MonthSelect({
  control,
  name,
  label,
}: {
  control: Control<FieldValues>;
  name: string;
  label: string;
}) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem className="lg:col-span-3">
          <FormLabel>{label}</FormLabel>
          <Select onValueChange={field.onChange} value={field.value ? String(field.value) : ""}>
            <FormControl>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="Chọn tháng" />
              </SelectTrigger>
            </FormControl>
            <SelectContent>
              {MONTHS.map((month) => (
                <SelectItem key={month} value={month}>
                  Tháng {month}
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

/**
 * The fields common to create and edit, from the name down. The year is the one
 * field that differs (a choice on create, fixed on edit), so the caller renders
 * it ahead of these.
 */
function CategoryFields({ form }: { form: UseFormReturn<FieldValues> }) {
  const control = form.control as Control<FieldValues>;
  const discountType = form.watch("discountType");
  const period = form.watch("applicationPeriod");

  return (
    <>
      <FormField
        control={control}
        name="name"
        rules={{
          required: "Vui lòng nhập tên",
          maxLength: { value: 255, message: "Tối đa 255 ký tự" },
        }}
        render={({ field }) => (
          <FormItem className="lg:col-span-8">
            <FormLabel>Tên diện ưu tiên</FormLabel>
            <FormControl>
              <Input {...field} value={field.value ?? ""} placeholder="Con thương binh" />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />

      <EnumSelect
        control={control}
        name="discountType"
        label="Loại miễn giảm"
        options={DISCOUNT_TYPE_LABELS}
        className="lg:col-span-4"
      />

      <FormField
        control={control}
        name="discountValue"
        rules={{
          required: "Vui lòng nhập mức miễn giảm",
          validate: (value) => {
            const number = Number(value);
            if (Number.isNaN(number) || number < 0) return "Mức miễn giảm không được âm";
            if (discountType === "PERCENT" && number > 100) {
              return "Mức miễn giảm theo phần trăm không được vượt quá 100%";
            }
            return true;
          },
        }}
        render={({ field }) => (
          <FormItem className="lg:col-span-4">
            <FormLabel>Mức miễn giảm</FormLabel>
            <FormControl>
              <Input {...field} type="number" min={0} step="any" inputMode="decimal" />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />

      <EnumSelect
        control={control}
        name="applicationPeriod"
        label="Thời gian áp dụng"
        options={APPLICATION_PERIOD_LABELS}
        className="lg:col-span-4"
      />

      {period === "CUSTOM" && (
        <>
          <MonthSelect control={control} name="fromMonth" label="Từ tháng" />
          <MonthSelect control={control} name="toMonth" label="Đến tháng" />
        </>
      )}
    </>
  );
}

/** What goes to the API: numbers as numbers, months only for a custom period. */
function toPayload(values: PriorityCategoryFormValues) {
  const custom = values.applicationPeriod === "CUSTOM";
  return {
    name: values.name,
    discountType: values.discountType,
    discountValue: Number(values.discountValue),
    applicationPeriod: values.applicationPeriod,
    fromMonth: custom && values.fromMonth ? Number(values.fromMonth) : null,
    toMonth: custom && values.toMonth ? Number(values.toMonth) : null,
  };
}

export const PriorityCategoryCreate = () => {
  const navigate = useNavigate();
  const { enabled, selected } = useAcademicYear();

  // A school works in the year chosen in the header; the system administrator
  // has no such year and picks one of any school.
  const { options: yearOptions, query: yearQuery } = useSelect({
    resource: "academic_years",
    optionLabel: (item) => {
      const year = item as unknown as { name: string; organization?: { name?: string } };
      return enabled ? year.name : `${year.name} · ${year.organization?.name ?? ""}`;
    },
    optionValue: "id",
    pagination: { currentPage: 1, pageSize: 100 },
    sorters: [{ field: "startDate", order: "desc" }],
  });

  const {
    refineCore: { onFinish },
    ...form
  } = useForm({
    refineCoreProps: {},
    defaultValues: DEFAULT_VALUES,
  });

  const yearId = form.watch("academicYear.id");
  useEffect(() => {
    if (!yearId && selected) form.setValue("academicYear.id", String(selected.id));
  }, [yearId, selected, form]);

  function onSubmit(values: PriorityCategoryFormValues) {
    onFinish({
      academicYear: { id: Number(values.academicYear.id) },
      ...toPayload(values),
    } as never);
  }

  return (
    <CreateView>
      <CreateViewHeader />
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FormPanel contentClassName="space-y-0">
            <div className="grid gap-6 lg:grid-cols-12">
              <FormField
                control={form.control}
                name="academicYear.id"
                rules={{ required: "Vui lòng chọn năm học" }}
                render={({ field }) => (
                  <FormItem className="lg:col-span-4">
                    <FormLabel>Năm học</FormLabel>
                    <Select
                      onValueChange={field.onChange}
                      value={field.value ? String(field.value) : ""}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue
                            placeholder={yearQuery.isLoading ? "Đang tải…" : "Chọn năm học"}
                          />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {(yearOptions ?? []).map((option) => (
                          <SelectItem
                            key={String(option.value)}
                            value={String(option.value)}
                          >
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <CategoryFields form={form as unknown as UseFormReturn<FieldValues>} />
            </div>

            <FormActions>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? "Đang tạo…" : "Thêm mới"}
              </Button>
              <Button type="button" variant="outline" onClick={() => navigate(-1)}>
                Hủy
              </Button>
            </FormActions>
          </FormPanel>
        </form>
      </Form>
    </CreateView>
  );
};

export const PriorityCategoryEdit = () => {
  const navigate = useNavigate();

  const {
    refineCore: { onFinish, query },
    ...form
  } = useForm({
    refineCoreProps: {},
  });

  const category = query?.data?.data as PriorityCategory | undefined;

  // The record arrives as numbers; the inputs hold strings, so convert once the
  // record is in rather than letting a number sit in a text field.
  useEffect(() => {
    if (!category) return;
    form.reset(
      {
        name: category.name,
        discountType: category.discountType,
        discountValue: String(category.discountValue),
        applicationPeriod: category.applicationPeriod,
        fromMonth: category.fromMonth ? String(category.fromMonth) : "",
        toMonth: category.toMonth ? String(category.toMonth) : "",
      },
      { keepDirtyValues: true }
    );
  }, [category, form]);

  // A row never changes year, so the year is not sent.
  function onSubmit(values: FieldValues) {
    onFinish(toPayload(values as PriorityCategoryFormValues) as never);
  }

  return (
    <EditView>
      <EditViewHeader />
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FormPanel contentClassName="space-y-0">
            {category ? (
              <div className="grid gap-6 lg:grid-cols-12">
                <FormItem className="lg:col-span-4">
                  <FormLabel>Năm học</FormLabel>
                  <Input value={category.academicYear.name} readOnly disabled />
                </FormItem>

                <CategoryFields form={form as unknown as UseFormReturn<FieldValues>} />
              </div>
            ) : null}

            <FormActions>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? "Đang lưu…" : "Lưu"}
              </Button>
              <Button type="button" variant="outline" onClick={() => navigate(-1)}>
                Hủy
              </Button>
            </FormActions>
          </FormPanel>
        </form>
      </Form>
    </EditView>
  );
};
