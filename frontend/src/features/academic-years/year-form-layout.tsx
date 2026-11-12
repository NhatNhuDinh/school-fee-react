import { useMemo, type ReactNode } from "react";
import { useWatch, type UseFormReturn } from "react-hook-form";

import { FormPanel } from "@/shared/components/form/form-panel";
import { DetailPanel } from "@/shared/components/views/record-detail";
import { buildTimeline } from "@/domains/academic-year/timeline";
import {
  previewYearName,
  type AcademicYearFormValues,
  type SchoolSummary,
} from "@/domains/academic-year/types";
import { YearFormFields } from "./year-form-fields";
import { YearSummary } from "./year-summary";
import { YearTimeline } from "./year-timeline";

type Props = {
  form: UseFormReturn<AcademicYearFormValues>;
  mode: "create" | "edit";
  currentSchool?: SchoolSummary;
  /** The save bar, rendered at the foot of the form panel. */
  children: ReactNode;
};

/**
 * The year screen, shared by create and edit: the year drawn across the top,
 * the form beneath it and a live summary beside the form.
 *
 * The drawing and the summary read the form's own values as they are typed, so
 * they are the preview of what Save will write. Beside each other only from
 * `@4xl` of the content column — measured on the column, not the window, since
 * the sidebar takes a quarter of a narrow one — and above the form, not under
 * it, when stacked: the answer comes before the questions.
 */
export function YearFormLayout({ form, mode, currentSchool, children }: Props) {
  const [startDate, endDate, periods] = useWatch({
    control: form.control,
    name: ["startDate", "endDate", "periods"],
  });
  const timeline = useMemo(
    () => buildTimeline(startDate, endDate, periods),
    [startDate, endDate, periods]
  );

  return (
    <div className="flex flex-col gap-4">
      <DetailPanel
        title="Lịch năm học · tự cập nhật theo form"
        bodyClassName="p-4 @lg:p-5"
      >
        <YearTimeline timeline={timeline} />
      </DetailPanel>

      <div className="grid items-start gap-4 @4xl/main:grid-cols-[minmax(0,1fr)_22rem]">
        <FormPanel className="@container/form">
          <YearFormFields
            form={form}
            mode={mode}
            currentSchool={currentSchool}
            timeline={timeline}
          />
          {children}
        </FormPanel>
        <YearSummary
          name={previewYearName(startDate, endDate)}
          school={currentSchool?.name}
          timeline={timeline}
          className="order-first @4xl/main:sticky @4xl/main:top-20 @4xl/main:order-none"
        />
      </div>
    </div>
  );
}
