import { Search, X, ChevronDown } from "lucide-react";
import React from "react";

import type { StudentHit } from "@/domains/fee-payment/types";
import { formatDate, formatNumber } from "@/shared/lib/format";
import { cn } from "@/shared/lib/utils";
import { Input } from "@/shared/ui/input";
import { Popover, PopoverAnchor, PopoverContent } from "@/shared/ui/popover";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/shared/ui/table";
import { useDebounced } from "@/shared/hooks/use-debounced";
import { searchStudents } from "./api";

/**
 * The student box of the counter: type a name or a code (a barcode reader types the code and
 * Enter), pick from what still owes. Only students with something to collect are offered.
 */
export function StudentSearch({
  yearId,
  label,
  onPick,
  onClear,
  openOnFocus = true,
  fitWidth = false,
}: {
  yearId: number;
  label: string | null;
  onPick: (hit: StudentHit) => void;
  onClear: () => void;
  /**
   * The counter opens the list the moment the box is focused, so the cashier sees who owes. A dialog focuses its
   * first field by itself, and a list that opens over the dialog before anyone has asked for it covers the rest of
   * it: there it opens on a click or on typing instead.
   */
  openOnFocus?: boolean;
  /** Make the list as wide as the box rather than the width of a counter screen, for a box inside a dialog. */
  fitWidth?: boolean;
}) {
  const [text, setText] = React.useState("");
  const [open, setOpen] = React.useState(false);
  const [hits, setHits] = React.useState<StudentHit[]>([]);
  const [active, setActive] = React.useState(0);
  const settled = useDebounced(text, 250);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const anchorRef = React.useRef<HTMLDivElement>(null);
  // In a dialog the list is drawn inside it (see `PopoverContent`), so the wheel scrolls it.
  const [host, setHost] = React.useState<HTMLElement | null>(null);
  React.useEffect(() => {
    setHost(fitWidth ? (anchorRef.current?.closest("[role=dialog]") as HTMLElement | null) : null);
  }, [fitWidth]);

  React.useEffect(() => {
    if (!open) {
      setHits([]);
      return;
    }
    let live = true;
    searchStudents(yearId, settled)
      .then((rows) => {
        if (!live) return;
        setHits(rows);
        setActive(0);
      })
      .catch(() => live && setHits([]));
    return () => {
      live = false;
    };
  }, [settled, open, yearId]);

  function pick(hit: StudentHit) {
    onPick(hit);
    setText("");
    setOpen(false);
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverAnchor asChild>
        <div ref={anchorRef} className={cn("bg-card border-input border-b-stroke-accessible hover:border-stroke-hover hover:border-b-stroke-accessible-hover focus-within:border-b-stroke-brand flex h-control w-full items-center gap-2 rounded-md border px-2.5 text-sm transition-[border-color,box-shadow] focus-within:shadow-[inset_0_-1px_0_0_var(--stroke-brand)]", !fitWidth && "max-w-xl")}>
          <Search className="text-muted-foreground size-4 shrink-0" />
          {label && !open && text === "" ? (
            <button
              type="button"
              className="min-w-0 flex-1 truncate text-left"
              onClick={() => {
                setOpen(true);
                requestAnimationFrame(() => inputRef.current?.focus());
              }}
            >
              {label}
            </button>
          ) : null}
          <Input
            ref={inputRef}
            appearance="bare"
            value={text}
            placeholder={label ? "" : "Nhập tên hoặc mã học sinh (quét mã vạch)…"}
            className={cn(
              "h-auto min-w-0 flex-1 p-0",
              label && !open && text === "" && "hidden"
            )}
            onFocus={() => openOnFocus && setOpen(true)}
            onClick={() => setOpen(true)}
            onChange={(event) => {
              setText(event.target.value);
              setOpen(true);
            }}
            onKeyDown={(event) => {
              if (event.key === "ArrowDown") {
                event.preventDefault();
                setActive((i) => Math.min(i + 1, Math.max(hits.length - 1, 0)));
              } else if (event.key === "ArrowUp") {
                event.preventDefault();
                setActive((i) => Math.max(i - 1, 0));
              } else if (event.key === "Enter" && hits[active]) {
                event.preventDefault();
                pick(hits[active]);
              } else if (event.key === "Escape") {
                setOpen(false);
              }
            }}
          />
          {label ? (
            <button type="button" aria-label="Bỏ chọn" className="text-muted-foreground hover:text-foreground" onClick={onClear}>
              <X className="size-4" />
            </button>
          ) : (
            <ChevronDown className="text-muted-foreground size-4" />
          )}
        </div>
      </PopoverAnchor>
      <PopoverContent
        align="start"
        container={host}
        className={cn(
          "overflow-hidden p-0",
          fitWidth ? "w-(--radix-popover-trigger-width)" : "w-[min(60rem,calc(100vw-2rem))]"
        )}
        onOpenAutoFocus={(event) => event.preventDefault()}
        onInteractOutside={(event) => {
          if (anchorRef.current?.contains(event.target as Node)) event.preventDefault();
        }}
      >
        {hits.length === 0 ? (
          <p className="text-muted-foreground px-4 py-3 text-sm">
            {text.trim()
              ? `Không có học sinh nào còn phải thu khớp “${text.trim()}”.`
              : "Chưa có học sinh nào còn phải thu."}
          </p>
        ) : (
          <Table containerClassName="max-h-80 overflow-y-auto" className="text-sm">
            <TableHeader sticky>
              <TableRow>
                <TableHead>Họ tên</TableHead>
                <TableHead className="w-32">Ngày sinh</TableHead>
                <TableHead className="w-24">Lớp</TableHead>
                <TableHead className="w-44">Mã học sinh</TableHead>
                <TableHead className="w-36 text-right">Còn phải thu</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {hits.map((hit, index) => (
                <TableRow
                  key={hit.enrollmentId}
                  className={cn("cursor-pointer", index === active && "bg-accent")}
                  onMouseEnter={() => setActive(index)}
                  onClick={() => pick(hit)}
                >
                  <TableCell className="font-semibold">{hit.fullName}</TableCell>
                  <TableCell>{formatDate(hit.dateOfBirth)}</TableCell>
                  <TableCell>{hit.className}</TableCell>
                  <TableCell>{hit.studentCode}</TableCell>
                  <TableCell className="text-brand text-right font-semibold tabular-nums">
                    {formatNumber(hit.outstanding)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </PopoverContent>
    </Popover>
  );
}
