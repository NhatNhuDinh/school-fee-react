import { ChevronDown, ChevronRight, Search } from "lucide-react";
import { useMemo, useState } from "react";

import type { StructureNode, StudentStructure } from "@/domains/student/types";
import { cn } from "@/shared/lib/utils";
import { Input } from "@/shared/ui/input";

type Props = {
  structure: StudentStructure | undefined;
  selected: StructureNode;
  onSelect: (node: StructureNode) => void;
  className?: string;
};

const sameNode = (a: StructureNode, b: StructureNode) =>
  a.type === b.type && ("id" in a && "id" in b ? a.id === b.id : true);

/**
 * The year's khối lớp and lớp with how many students each holds, beside the
 * student list. Picking a row narrows the list to it. The first two rows are not
 * part of the school's structure: everybody, and those not yet in a class.
 *
 * Typing in the search box keeps the khối and lớp whose name contains the text,
 * and a khối stays when any of its lớp does.
 */
export function StructureTree({ structure, selected, onSelect, className }: Props) {
  const [search, setSearch] = useState("");
  const [collapsed, setCollapsed] = useState<Set<number>>(new Set());

  const groups = useMemo(() => {
    const term = search.trim().toLowerCase();
    const all = structure?.groups ?? [];
    if (!term) return all;
    return all
      .map((group) => {
        const groupMatches = group.name.toLowerCase().includes(term);
        const classes = group.classes.filter((c) => c.name.toLowerCase().includes(term));
        return groupMatches ? group : { ...group, classes };
      })
      .filter((group) => group.name.toLowerCase().includes(term) || group.classes.length > 0);
  }, [structure, search]);

  function toggle(id: number) {
    setCollapsed((current) => {
      const next = new Set(current);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }

  return (
    <div className={cn("flex min-h-0 flex-col", className)}>
      <div className="border-b p-3">
        <div className="relative">
          <Search className="text-muted-foreground absolute start-3 top-1/2 size-4 -translate-y-1/2" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Tìm khối, lớp…"
            className="ps-9"
          />
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto py-1" role="tree">
        <Row
          label="Tất cả"
          count={structure?.total}
          active={sameNode(selected, { type: "all" })}
          onClick={() => onSelect({ type: "all" })}
        />
        <Row
          label="Chưa xếp lớp"
          count={structure?.unassigned}
          active={sameNode(selected, { type: "unassigned" })}
          onClick={() => onSelect({ type: "unassigned" })}
        />
        {groups.map((group) => {
          const open = search.trim() !== "" || !collapsed.has(group.id);
          return (
            <div key={group.id}>
              <Row
                label={group.name}
                bold
                count={group.studentCount}
                active={selected.type === "group" && selected.id === group.id}
                onClick={() => onSelect({ type: "group", id: group.id })}
                lead={
                  <button
                    type="button"
                    aria-label={open ? "Thu gọn" : "Mở rộng"}
                    className="text-muted-foreground hover:text-foreground -ms-1 me-1 rounded p-0.5"
                    onClick={(event) => {
                      event.stopPropagation();
                      toggle(group.id);
                    }}
                  >
                    {open ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
                  </button>
                }
              />
              {open &&
                group.classes.map((klass) => (
                  <Row
                    key={klass.id}
                    label={klass.name}
                    count={klass.studentCount}
                    indent
                    active={selected.type === "class" && selected.id === klass.id}
                    onClick={() => onSelect({ type: "class", id: klass.id })}
                  />
                ))}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Row({
  label,
  count,
  active,
  indent,
  bold,
  lead,
  onClick,
}: {
  label: string;
  count: number | undefined;
  active: boolean;
  indent?: boolean;
  /** A khối: names a group of rows rather than one of them. */
  bold?: boolean;
  lead?: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <div
      role="treeitem"
      aria-selected={active}
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onClick();
        }
      }}
      className={cn(
        "flex cursor-pointer items-center justify-between gap-2 px-4 py-2 text-sm",
        "hover:bg-accent/50 focus-visible:bg-accent/50 outline-none",
        indent && "ps-10",
        bold && "font-semibold",
        active && "bg-primary/10 text-primary font-medium"
      )}
    >
      <span className="flex min-w-0 items-center">
        {lead}
        <span className="truncate">{label}</span>
      </span>
      <span className="text-muted-foreground tabular-nums">{count ?? "–"}</span>
    </div>
  );
}
