import { useSelect } from "@refinedev/core";
import { useForm } from "@refinedev/react-hook-form";
import { useEffect } from "react";
import type { FieldValues, UseFormReturn } from "react-hook-form";
import { useNavigate } from "react-router";

import type {
  ClassGroup,
  ClassGroupFormValues,
} from "@/domains/class-group/types";
import { FormActions, FormPanel } from "@/shared/components/form/form-panel";
import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
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
import { ClassesPanel } from "./classes-panel";

const DEFAULT_VALUES: ClassGroupFormValues = {
  academicYear: { id: "" },
  name: "",
};

function NameField({ form }: { form: UseFormReturn<FieldValues> }) {
  return (
    <FormField
      control={form.control}
      name="name"
      rules={{
        required: "Vui lòng nhập tên",
        maxLength: { value: 255, message: "Tối đa 255 ký tự" },
      }}
      render={({ field }) => (
        <FormItem className="lg:col-span-8">
          <FormLabel>Tên khối lớp</FormLabel>
          <FormControl>
            <Input {...field} value={field.value ?? ""} placeholder="Khối 10" />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}

export const ClassGroupCreate = () => {
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
    // A khối lớp is useless without its lớp, which are added on the edit page.
    refineCoreProps: { redirect: "edit" },
    defaultValues: DEFAULT_VALUES,
  });

  const yearId = form.watch("academicYear.id");
  useEffect(() => {
    if (!yearId && selected) form.setValue("academicYear.id", String(selected.id));
  }, [yearId, selected, form]);

  function onSubmit({ academicYear, name }: ClassGroupFormValues) {
    onFinish({ academicYear: { id: Number(academicYear.id) }, name } as never);
  }

  return (
    <CreateView>
      <CreateViewHeader />
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FormPanel>
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

              <NameField form={form as unknown as UseFormReturn<FieldValues>} />
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

export const ClassGroupEdit = () => {
  const navigate = useNavigate();

  const {
    refineCore: { onFinish, query },
    ...form
  } = useForm({
    refineCoreProps: {},
  });

  const group = query?.data?.data as ClassGroup | undefined;

  // A row never changes year, so only the name goes back.
  function onSubmit(values: FieldValues) {
    onFinish({ name: values.name });
  }

  return (
    <EditView>
      <EditViewHeader />
      <div className="space-y-6">
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)}>
            <FormPanel>
              <div className="grid gap-6 lg:grid-cols-12">
                <FormItem className="lg:col-span-4">
                  <FormLabel>Năm học</FormLabel>
                  <Input value={group?.academicYear.name ?? ""} readOnly disabled />
                </FormItem>

                <NameField form={form as unknown as UseFormReturn<FieldValues>} />
              </div>

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

        {group && <ClassesPanel classGroupId={group.id} />}
      </div>
    </EditView>
  );
};
