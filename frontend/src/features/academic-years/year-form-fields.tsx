import { useCan, useSelect } from "@refinedev/core";
import { useMemo } from "react";
import { useFieldArray, useWatch, type UseFormReturn } from "react-hook-form";
import { Plus, Trash2 } from "lucide-react";

import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/shared/ui/form";
import {
  previewYearName,
  type AcademicYearFormValues,
  type SchoolSummary,
} from "@/domains/academic-year/types";
import { termColor, type YearTimeline } from "@/domains/academic-year/timeline";

type Props = {
  form: UseFormReturn<AcademicYearFormValues>;
  /**
   * `edit` shows the school as fixed: a year does not move between schools, and
   * the API's patch has no field for it.
   */
  mode: "create" | "edit";
  currentSchool?: SchoolSummary;
  /** What the dates typed so far add up to, for the line under each term. */
  timeline: YearTimeline | null;
};

/** A heading for a group of fields; the one small-caps style the shell has. */
function Heading({ children }: { children: string }) {
  return <h3 className="text-overline text-muted-foreground">{children}</h3>;
}

/**
 * The fields of an academic year, shared by create and edit.
 *
 * The year's name is not a field. It is derived from the two dates by the API,
 * so it is shown as a hint under them rather than as a value somebody could get
 * out of step with them.
 *
 * Widths here come from the panel's own container (`@xl/form`), not the window:
 * beside the summary the panel is far narrower than the viewport says.
 */
export function YearFormFields({ form, mode, currentSchool, timeline }: Props) {
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
    queryOptions: { enabled: mayReadOrganizations && mode === "create" },
  });

  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: "periods",
  });

  const [startDate, endDate] = useWatch({
    control: form.control,
    name: ["startDate", "endDate"],
  });

  const options = useMemo(() => schoolOptions ?? [], [schoolOptions]);

  return (
    <>
      <div className="space-y-6">
        <Heading>Thông tin năm học</Heading>

        {mode === "create" ? (
          <FormField
            control={form.control}
            name="organization.id"
            render={({ field }) => (
              <FormItem>
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
                    {options.map((option) => (
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
        ) : (
          <FormItem>
            <FormLabel>Trường (đơn vị)</FormLabel>
            <Input value={currentSchool?.name ?? ""} readOnly disabled />
          </FormItem>
        )}

        <div className="grid gap-6 @xl/form:grid-cols-2">
          <FormField
            control={form.control}
            name="startDate"
            rules={{ required: "Vui lòng nhập ngày bắt đầu" }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>Ngày bắt đầu</FormLabel>
                <FormControl>
                  <Input {...field} type="date" value={field.value ?? ""} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="endDate"
            rules={{ required: "Vui lòng nhập ngày kết thúc" }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>Ngày kết thúc</FormLabel>
                <FormControl>
                  <Input {...field} type="date" value={field.value ?? ""} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
        <p className="text-muted-foreground -mt-3 text-xs">
          Tên năm học tự đặt theo hai năm: {previewYearName(startDate, endDate)}
        </p>
      </div>

      <div className="space-y-4 border-t pt-6">
        <div className="flex items-center justify-between gap-3">
          <Heading>Học kỳ</Heading>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => append({ name: "", startDate: "", endDate: "" })}
          >
            <Plus className="h-4 w-4" />
            Thêm học kỳ
          </Button>
        </div>

        {fields.length === 0 ? (
          <p className="text-muted-foreground rounded-md border border-dashed p-4 text-center text-sm">
            Chưa có học kỳ nào. Mỗi học kỳ nằm trong năm học và không trùng với học
            kỳ khác.
          </p>
        ) : null}

        {fields.map((item, index) => {
          const drawn = timeline?.terms.find((t) => t.index === index);
          return (
            <div
              key={item.id}
              className="bg-card relative grid grid-cols-2 gap-3 rounded-lg border py-3 pr-3 pl-5 @xl/form:grid-cols-[minmax(0,1fr)_9.5rem_9.5rem_auto]"
            >
              <span
                aria-hidden
                className="absolute inset-y-0 left-0 w-1.5 rounded-l-lg"
                style={{ background: termColor(index) }}
              />
              <FormField
                control={form.control}
                name={`periods.${index}.name`}
                rules={{ required: "Vui lòng nhập tên" }}
                render={({ field }) => (
                  <FormItem className="col-span-2 @max-xl/form:pr-9 @xl/form:col-span-1">
                    <FormLabel className="text-xs">Tên học kỳ</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        value={field.value ?? ""}
                        placeholder="Học kỳ I"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name={`periods.${index}.startDate`}
                rules={{ required: "Bắt buộc" }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel className="text-xs">Bắt đầu</FormLabel>
                    <FormControl>
                      <Input {...field} type="date" value={field.value ?? ""} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name={`periods.${index}.endDate`}
                rules={{ required: "Bắt buộc" }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel className="text-xs">Kết thúc</FormLabel>
                    <FormControl>
                      <Input {...field} type="date" value={field.value ?? ""} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label="Xóa học kỳ"
                className="absolute top-2 right-2 @xl/form:static @xl/form:mt-5"
                onClick={() => remove(index)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>

              {drawn && (
                <p
                  className={
                    drawn.outside || drawn.overlaps
                      ? "text-destructive col-span-full text-xs"
                      : "text-muted-foreground col-span-full text-xs"
                  }
                >
                  {drawn.outside
                    ? "Nằm ngoài năm học."
                    : drawn.overlaps
                      ? "Trùng với học kỳ khác."
                      : `${drawn.days} ngày`}
                </p>
              )}
            </div>
          );
        })}
        {/* The API reports term problems under one key, `periods`. */}
        <FormField
          control={form.control}
          name="periods"
          render={() => <FormMessage />}
        />
      </div>
    </>
  );
}
