import { useForm } from "@refinedev/react-hook-form";
import { useNavigate } from "react-router";

import {
  CreateView,
  CreateViewHeader,
} from "@/shared/components/views/create-view";
import { Button } from "@/shared/ui/button";
import { Form } from "@/shared/ui/form";
import { FormActions } from "@/shared/components/form/form-panel";
import type { AcademicYearFormValues } from "@/domains/academic-year/types";
import { YearFormLayout } from "./year-form-layout";

const DEFAULT_VALUES: AcademicYearFormValues = {
  organization: { id: "" },
  startDate: "",
  endDate: "",
  periods: [],
};

export const AcademicYearCreate = () => {
  const navigate = useNavigate();

  const {
    refineCore: { onFinish },
    ...form
  } = useForm({
    refineCoreProps: {},
    defaultValues: DEFAULT_VALUES,
  });

  function onSubmit({ organization, ...values }: AcademicYearFormValues) {
    // No school chosen means "mine": leave the field out and the API decides.
    onFinish((organization.id ? { organization, ...values } : values) as AcademicYearFormValues);
  }

  return (
    <CreateView>
      <CreateViewHeader />
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <YearFormLayout form={form as never} mode="create">
            <FormActions>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? "Đang tạo…" : "Thêm mới"}
              </Button>
              <Button type="button" variant="outline" onClick={() => navigate(-1)}>
                Hủy
              </Button>
            </FormActions>
          </YearFormLayout>
        </form>
      </Form>
    </CreateView>
  );
};
