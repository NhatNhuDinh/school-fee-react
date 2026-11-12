import { useEffect } from "react";
import { useForm } from "@refinedev/react-hook-form";
import { useNavigate, useParams } from "react-router";

import {
  EditView,
  EditViewHeader,
} from "@/shared/components/views/edit-view";
import { Button } from "@/shared/ui/button";
import { Form } from "@/shared/ui/form";
import { FormActions } from "@/shared/components/form/form-panel";
import type {
  AcademicYear,
  AcademicYearFormValues,
} from "@/domains/academic-year/types";
import { YearFormLayout } from "./year-form-layout";

/**
 * Remounted per year. The form keeps what somebody has typed when the record
 * arrives (`keepDirtyValues`), so moving to another year inside the same mounted
 * form would carry the old year's unsaved edits onto the new one.
 */
export const AcademicYearEdit = () => {
  const { id } = useParams();
  return <AcademicYearEditForm key={id} />;
};

const AcademicYearEditForm = () => {
  const navigate = useNavigate();

  const {
    refineCore: { onFinish, query },
    ...form
  } = useForm({
    refineCoreProps: {},
  });

  const year = query?.data?.data as AcademicYear | undefined;

  // The record becomes the form's values in one go, for the reason the
  // department edit screen spells out: the library's own fill misses fields
  // that are not registered at the instant it runs.
  const { reset } = form;
  useEffect(() => {
    if (!year) return;
    reset(
      {
        organization: { id: String(year.organization.id) },
        startDate: year.startDate,
        endDate: year.endDate,
        periods: year.periods,
      },
      { keepDirtyValues: true }
    );
  }, [year, reset]);

  // The school is not patchable, so it is left out of what is sent.
  function onSubmit(values: Record<string, unknown>) {
    const { startDate, endDate, periods } = values as AcademicYearFormValues;
    onFinish({ startDate, endDate, periods });
  }

  return (
    <EditView>
      <EditViewHeader />
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <YearFormLayout
            form={form as never}
            mode="edit"
            currentSchool={year?.organization}
          >
            <FormActions>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? "Đang lưu…" : "Lưu"}
              </Button>
              <Button type="button" variant="outline" onClick={() => navigate(-1)}>
                Hủy
              </Button>
            </FormActions>
          </YearFormLayout>
        </form>
      </Form>
    </EditView>
  );
};
