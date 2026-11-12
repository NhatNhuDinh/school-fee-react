import { Clock } from "lucide-react";
import * as React from "react";

import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/shared/ui/popover";

const HOURS = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, "0"));
const MINUTES = Array.from({ length: 60 }, (_, i) => String(i).padStart(2, "0"));

/**
 * A time field in 24-hour form: a button showing `HH:mm` that opens two scrollable columns,
 * hours and minutes, in a popover. The value is the `HH:mm` string; the browser's own
 * 12/24-hour setting and clock widget play no part.
 */
export function TimePicker({
  value,
  onChange,
  placeholder = "Chọn giờ",
  invalid,
  className,
  id,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  invalid?: boolean;
  className?: string;
  id?: string;
}) {
  const [open, setOpen] = React.useState(false);
  const [hour, minute] = /^\d{2}:\d{2}$/.test(value) ? value.split(":") : ["", ""];

  const pick = (nextHour: string, nextMinute: string) => onChange(`${nextHour || "00"}:${nextMinute || "00"}`);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          aria-invalid={invalid}
          className={cn(
            "w-full justify-between px-3 font-normal tabular-nums",
            !value && "text-muted-foreground",
            invalid && "border-destructive",
            className
          )}
        >
          {value || placeholder}
          <Clock className="text-muted-foreground size-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-2" align="start" onWheel={(event) => event.stopPropagation()}>
        <div className="flex gap-1">
          <Column label="Giờ" options={HOURS} selected={hour} onPick={(next) => pick(next, minute)} />
          <Column
            label="Phút"
            options={MINUTES}
            selected={minute}
            onPick={(next) => {
              pick(hour, next);
              setOpen(false);
            }}
          />
        </div>
      </PopoverContent>
    </Popover>
  );
}

function Column({
  label,
  options,
  selected,
  onPick,
}: {
  label: string;
  options: string[];
  selected: string;
  onPick: (value: string) => void;
}) {
  // Open with the chosen value in view rather than at the top of a 60-row list.
  const chosen = React.useRef<HTMLButtonElement>(null);
  React.useEffect(() => {
    chosen.current?.scrollIntoView({ block: "center" });
  }, []);

  return (
    <div role="listbox" aria-label={label} className="scrollbar-slim flex h-56 w-14 flex-col gap-0.5 overflow-y-auto pe-1">
      {options.map((option) => {
        const active = option === selected;
        return (
          <button
            key={option}
            ref={active ? chosen : undefined}
            type="button"
            role="option"
            aria-selected={active}
            onClick={() => onPick(option)}
            className={cn(
              "shrink-0 rounded-md py-1.5 text-center text-sm tabular-nums outline-none",
              "hover:bg-accent focus-visible:ring-ring/50 focus-visible:ring-2",
              active && "bg-primary text-primary-foreground hover:bg-primary-hover font-semibold"
            )}
          >
            {option}
          </button>
        );
      })}
    </div>
  );
}
