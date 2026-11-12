import { useCan, useSelect } from "@refinedev/core";
import { useForm } from "@refinedev/react-hook-form";
import type { FieldValues, UseFormReturn } from "react-hook-form";
import { useNavigate } from "react-router";

import {
  CreateView,
  CreateViewHeader,
} from "@/shared/components/views/create-view";
import { EditView, EditViewHeader } from "@/shared/components/views/edit-view";
import { FormActions, FormPanel } from "@/shared/components/form/form-panel";
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
import type { SchoolCatalogFormValues } from "@/domains/school-catalog/types";

export type CatalogFormProps = {
  /** Label of the name field, e.g. "Tên hệ đào tạo". */
  nameLabel: string;
  placeholder: string;
};

const DEFAULT_VALUES: SchoolCatalogFormValues = {
  organization: { id: "" },
  name: "",
};

function NameField({
  form,
  nameLabel,
  placeholder,
}: CatalogFormProps & { form: UseFormReturn<FieldValues> }) {
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
          <FormLabel>{nameLabel}</FormLabel>
          <FormControl>
            <Input {...field} value={field.value ?? ""} placeholder={placeholder} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}

export const CatalogCreate = (props: CatalogFormProps) => {
  const navigate = useNavigate();

  // Choosing a school is for the system administrator, who belongs to none;
  // everybody else creates for their own school.
  const { data: canReadOrganizations } = useCan({
    resource: "organizations",
    action: "list",
  });
  const mayReadOrganizations = canReadOrganizations?.can ?? false;

  const { options: schoolOptions, query: schoolQuery } = useSelect({
    resource: "organizations",
    optionLabel: "name",
    optionValue: "id",
    sorters: [{ field: "name", order: "asc" }],
    queryOptions: { enabled: mayReadOrganizations },
  });

  const {
    refineCore: { onFinish },
    ...form
  } = useForm({
    refineCoreProps: {},
    defaultValues: DEFAULT_VALUES,
  });

  function onSubmit({ organization, name }: SchoolCatalogFormValues) {
    // No school chosen means "mine": leave the field out and the API decides.
    onFinish((organization.id ? { organization, name } : { name }) as SchoolCatalogFormValues);
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
              name="organization.id"
              render={({ field }) => (
                <FormItem className="lg:col-span-4">
                  <FormLabel>Trường (đơn vị)</FormLabel>
                  <Select
                    onValueChange={field.onChange}
                    value={field.value ? String(field.value) : ""}
                    disabled={!mayReadOrganizations}
                  >
                    <FormControl>
                      <SelectTrigger className="w-full">
                        <SelectValue
                          placeholder={
                            schoolQuery.isLoading ? "Đang tải…" : "Trường của bạn"
                          }
                        />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {(schoolOptions ?? []).map((option) => (
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

            <NameField form={form as unknown as UseFormReturn<FieldValues>} {...props} />
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

export const CatalogEdit = (props: CatalogFormProps) => {
  const navigate = useNavigate();

  const {
    refineCore: { onFinish, query },
    ...form
  } = useForm({
    refineCoreProps: {},
  });

  // A row never changes school, so only the name goes back.
  function onSubmit(values: FieldValues) {
    onFinish({ name: values.name });
  }

  const schoolName =
    (query?.data?.data as { organization?: { name?: string } } | undefined)
      ?.organization?.name ?? "";

  return (
    <EditView>
      <EditViewHeader />
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FormPanel>
            <div className="grid gap-6 lg:grid-cols-12">
            <FormItem className="lg:col-span-4">
              <FormLabel>Trường (đơn vị)</FormLabel>
              <Input value={schoolName} readOnly disabled />
            </FormItem>

            <NameField form={form as unknown as UseFormReturn<FieldValues>} {...props} />
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
    </EditView>
  );
};
