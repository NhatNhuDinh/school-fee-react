import type { ReactNode } from "react";
import { Cell, Pie, PieChart } from "recharts";

export type DonutSlice = { name: string; value: number; color: string };

/**
 * A thin ring with the figure that matters in the middle. Square and fixed in size: a donut that
 * stretches to its card stops being a circle, and the card around it is the one that should flex.
 */
export function Donut({ slices, size, children }: { slices: DonutSlice[]; size: number; children?: ReactNode }) {
  const live = slices.filter((s) => s.value > 0);
  // With nothing to show, an empty track is still a ring rather than a hole in the card.
  const data = live.length > 0 ? live : [{ name: "—", value: 1, color: "var(--muted)" }];
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <PieChart width={size} height={size}>
        <Pie
          data={data}
          dataKey="value"
          nameKey="name"
          innerRadius="76%"
          outerRadius="100%"
          startAngle={90}
          endAngle={-270}
          paddingAngle={data.length > 1 ? 2 : 0}
          cornerRadius={5}
          stroke="none"
          isAnimationActive={false}
        >
          {data.map((slice) => (
            <Cell key={slice.name} fill={slice.color} />
          ))}
        </Pie>
      </PieChart>
      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center">
        {children}
      </div>
    </div>
  );
}

/** A coloured dot, a label that takes the room, and a figure against the right edge. */
export function LegendRow({ color, label, value }: { color: string; label: string; value: ReactNode }) {
  return (
    <li className="flex items-center gap-2.5 text-sm">
      <span className="size-2.5 shrink-0 rounded-full" style={{ background: color }} />
      <span className="text-muted-foreground min-w-0 flex-1 truncate">{label}</span>
      <span className="text-foreground font-semibold tabular-nums">{value}</span>
    </li>
  );
}
