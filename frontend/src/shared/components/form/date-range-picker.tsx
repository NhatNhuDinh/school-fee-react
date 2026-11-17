import { endOfMonth, format, isValid, parseISO, startOfMonth, subDays, subMonths } from "date-fns";
import { vi } from "date-fns/locale";
import { CalendarIcon } from "lucide-react";
import * as React from "react";
import type { DateRange } from "react-day-picker";

import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Calendar } from "@/shared/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/shared/ui/popover";

/** A range as the API takes it: plain `yyyy-MM-dd` strings, so no timezone can move an edge by a day. */
export type DateRangeValue = { from?: string; to?: string };

const ISO = "yyyy-MM-dd";
const SHOWN = "dd/MM/yyyy";

type Preset = { key: string; label: string; range: () => DateRange };

/** The quick ranges beside the calendars. Each is computed when picked, so "today" is today. */
const PRESETS: Preset[] = [
  { key: "today", label: "Hôm nay", range: () => ({ from: new Date(), to: new Date() }) },
  { key: "yesterday", label: "Hôm qua", range: () => ({ from: subDays(new Date(), 1), to: subDays(new Date(), 1) }) },
  { key: "7d", label: "7 ngày qua", range: () => ({ from: subDays(new Date(), 6), to: new Date() }) },
  { key: "30d", label: "30 ngày qua", range: () => ({ from: subDays(new Date(), 29), to: new Date() }) },
  { key: "month", label: "Tháng này", range: () => ({ from: startOfMonth(new Date()), to: new Date() }) },
  {
    key: "last-month",
    label: "Tháng trước",
    range: () => ({ from: startOfMonth(subMonths(new Date(), 1)), to: endOfMonth(subMonths(new Date(), 1)) }),
  },
];

/** The value "Tháng này" stands for, so a screen can open on it without knowing how it is computed. */
export function thisMonth(): DateRangeValue {
  const range = PRESETS[4].range();
  return { from: format(range.from!, ISO), to: format(range.to!, ISO) };
}

function toRange(value: DateRangeValue): DateRange | undefined {
  const from = value.from ? parseISO(value.from) : undefined;
  const to = value.to ? parseISO(value.to) : undefined;
  if (!from || !isValid(from)) return undefined;
  return { from, to: to && isValid(to) ? to : undefined };
}

function sameDay(a?: Date, b?: Date) {
  return Boolean(a && b && format(a, ISO) === format(b, ISO));
}

/**
 * A date-range field: it shows the range and opens two months side by side, with the usual quick ranges down
 * the left. Nothing changes until "Áp dụng", so choosing the first day and wandering off does not filter the
 * list by half a range.
 */
export function DateRangePicker({
  value,
  onChange,
  placeholder = "Chọn khoảng ngày",
  className,
}: {
  value: DateRangeValue;
  onChange: (value: DateRangeValue) => void;
  placeholder?: string;
  className?: string;
}) {
  const [open, setOpen] = React.useState(false);
  const [draft, setDraft] = React.useState<DateRange | undefined>(toRange(value));
  const committed = toRange(value);

  // Reopening starts from what is applied, not from an abandoned draft.
  React.useEffect(() => {
    if (open) setDraft(toRange(value));
  }, [open, value]);

  const label = committed?.from
    ? `${format(committed.from, SHOWN)}  –  ${format(committed.to ?? committed.from, SHOWN)}`
    : placeholder;

  const days = draft?.from ? Math.round(((draft.to ?? draft.from).getTime() - draft.from.getTime()) / 86_400_000) + 1 : 0;
  const activePreset = PRESETS.find((p) => {
    const range = p.range();
    return sameDay(range.from, draft?.from) && sameDay(range.to, draft?.to ?? draft?.from);
  });

  function apply() {
    if (!draft?.from) return;
    onChange({ from: format(draft.from, ISO), to: format(draft.to ?? draft.from, ISO) });
    setOpen(false);
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          className={cn("w-64 justify-start gap-2 px-2.5 font-normal", !committed && "text-muted-foreground", className)}
        >
          <CalendarIcon className="text-muted-foreground size-4" />
          {label}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto overflow-hidden p-0 shadow-fl-16">
        <div className="flex">
          <ul className="bg-surface-subtle flex w-44 shrink-0 flex-col gap-0.5 p-2" aria-label="Khoảng ngày thường dùng">
            {PRESETS.map((preset) => (
              <li key={preset.key}>
                <button
                  type="button"
                  onClick={() => setDraft(preset.range())}
                  className={cn(
                    "focus-ring hover:bg-accent w-full rounded-sm px-2.5 py-1.5 text-left text-sm",
                    activePreset?.key === preset.key && "bg-brand-subtle hover:bg-brand-subtle font-semibold"
                  )}
                >
                  {preset.label}
                </button>
              </li>
            ))}
            <li>
              <span
                className={cn(
                  "block w-full rounded-sm px-2.5 py-1.5 text-left text-sm",
                  !activePreset && draft?.from && "bg-brand-subtle font-semibold"
                )}
              >
                Tùy chọn
              </span>
            </li>
          </ul>
          <div className="flex flex-col p-3">
            <Calendar
              mode="range"
              locale={vi}
              numberOfMonths={2}
              weekStartsOn={1}
              selected={draft}
              defaultMonth={draft?.from ? startOfMonth(draft.from) : new Date()}
              onSelect={setDraft}
              className="p-0"
              classNames={{ months: "flex flex-row gap-4" }}
            />
            <div className="mt-2 flex items-center justify-between gap-6 border-t pt-3">
              <span className="text-muted-foreground text-xs">
                {draft?.from
                  ? `${format(draft.from, SHOWN)} – ${format(draft.to ?? draft.from, SHOWN)} · ${days} ngày`
                  : "Chưa chọn ngày"}
              </span>
              <div className="flex gap-2">
                <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                  Hủy
                </Button>
                <Button type="button" disabled={!draft?.from} onClick={apply}>
                  Áp dụng
                </Button>
              </div>
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
