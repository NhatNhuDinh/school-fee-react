// Compares the Fluent 2 tokens in src/styles/theme.css (sections 9 and 10) with @fluentui/tokens.
//
//   npm run verify:fluent-tokens
//
// A number in theme.css that is not Fluent's is a bug that looks like a design choice, so this
// fails (exit 1) on any difference. Each row is [css variable, light token, dark token]; a row with
// one token name uses it for both modes. A `null` side is a variable the mode does not set.
import { readFileSync } from "node:fs";
import { webDarkTheme, webLightTheme } from "@fluentui/tokens";

const css = readFileSync(new URL("../src/styles/theme.css", import.meta.url), "utf8");

/** Every rule whose selector is exactly `selector`, joined: `:root` and `.dark` each appear in many sections. */
function rule(selector) {
  const found = [];
  let from = 0;
  for (;;) {
    const at = css.indexOf(`\n${selector} {`, from);
    if (at < 0) break;
    const end = css.indexOf("\n}", at);
    found.push(css.slice(at, end));
    from = end;
  }
  return found.length ? found.join("\n") : undefined;
}

/** The last declaration of `name` in the text: a later one in the same selector wins, as it does in CSS. */
const read = (text, name) => {
  const all = [...(text ?? "").matchAll(new RegExp(`\\n\\s*${name}:\\s*([^;]+);`, "g"))];
  return all.length ? all[all.length - 1][1].trim() : undefined;
};

// Compare numbers and hex colours, not syntax: rgb(0 0 0 / .12) is rgba(0,0,0,0.12).
const norm = (value) =>
  value === undefined
    ? undefined
    : /^#/.test(value)
      ? value.toLowerCase()
      : (String(value).match(/-?\d*\.?\d+/g) ?? []).map((n) => String(Number(n))).join(" ");

const same = (token) => [token, token];

// ---- Section 9: not tied to a preset, one rule per mode -------------------------------------------
const durations = {
  "ultra-fast": "durationUltraFast",
  faster: "durationFaster",
  fast: "durationFast",
  normal: "durationNormal",
  gentle: "durationGentle",
  slow: "durationSlow",
  slower: "durationSlower",
  "ultra-slow": "durationUltraSlow",
};
const curves = {
  "accelerate-max": "curveAccelerateMax",
  "accelerate-mid": "curveAccelerateMid",
  "accelerate-min": "curveAccelerateMin",
  "decelerate-max": "curveDecelerateMax",
  "decelerate-mid": "curveDecelerateMid",
  "decelerate-min": "curveDecelerateMin",
  "easy-ease-max": "curveEasyEaseMax",
  "easy-ease": "curveEasyEase",
};

const section9 = [
  ...[2, 4, 8, 16, 28, 64].map((n) => [`--shadow-fl-${n}`, ...same(`shadow${n}`)]),
  ["--focus-inner", ...same("colorStrokeFocus2")],
  ["--focus-outer", ...same("colorStrokeFocus1")],
  ["--disabled-bg", ...same("colorNeutralBackgroundDisabled")],
  ["--disabled-line", ...same("colorNeutralStrokeDisabled")],
  ["--disabled-fg", ...same("colorNeutralForegroundDisabled")],
  ["--stroke-hover", ...same("colorNeutralStroke1Hover")],
  ["--stroke-pressed", ...same("colorNeutralStroke1Pressed")],
  ["--pressed-bg", ...same("colorNeutralBackground1Pressed")],
  ["--selected-bg", ...same("colorNeutralBackground1Selected")],
  ["--stroke-accessible", ...same("colorNeutralStrokeAccessible")],
  ["--stroke-accessible-hover", ...same("colorNeutralStrokeAccessibleHover")],
  ["--rating", ...same("colorPaletteMarigoldBackground3")],
];
const section9Light = [
  ...section9,
  ...Object.entries(durations).map(([k, t]) => [`--dur-${k}`, t, null]),
  ...Object.entries(curves).map(([k, t]) => [`--curve-${k}`, t, null]),
  ["--stroke-thin", "strokeWidthThin", null],
  ["--stroke-thick", "strokeWidthThick", null],
  ["--stroke-thicker", "strokeWidthThicker", null],
  ["--radius-fl-small", "borderRadiusSmall", null],
  ["--radius-fl-medium", "borderRadiusMedium", null],
  ["--radius-fl-large", "borderRadiusLarge", null],
  ["--radius-fl-xlarge", "borderRadiusXLarge", null],
];

// ---- Section 10: the `fluent` preset --------------------------------------------------------------
const surfaces = [
  ["--background", ...same("colorNeutralBackground3")],
  ["--foreground", ...same("colorNeutralForeground1")],
  ["--card", ...same("colorNeutralBackground1")],
  ["--card-foreground", ...same("colorNeutralForeground1")],
  ["--popover", ...same("colorNeutralBackground1")],
  ["--popover-foreground", ...same("colorNeutralForeground1")],
  ["--surface-subtle", ...same("colorNeutralBackground2")],
  ["--table-header", ...same("colorNeutralBackground1")],
  ["--table-header-foreground", ...same("colorNeutralForeground1")],
  ["--primary", ...same("colorBrandBackground")],
  ["--primary-foreground", ...same("colorNeutralForegroundOnBrand")],
  ["--primary-hover", ...same("colorBrandBackgroundHover")],
  ["--primary-pressed", ...same("colorBrandBackgroundPressed")],
  ["--brand-foreground", ...same("colorBrandForeground1")],
  ["--brand-subtle", ...same("colorBrandBackground2")],
  ["--brand-subtle-hover", ...same("colorBrandBackground2Hover")],
  ["--secondary", "colorNeutralBackground3", "colorNeutralBackground6"],
  ["--secondary-foreground", ...same("colorNeutralForeground1")],
  ["--muted", "colorNeutralBackground3", "colorNeutralBackground6"],
  ["--muted-foreground", ...same("colorNeutralForeground3")],
  ["--accent", ...same("colorSubtleBackgroundHover")],
  ["--accent-foreground", ...same("colorNeutralForeground1")],
  ["--destructive", ...same("colorStatusDangerBackground3")],
  ["--destructive-foreground", ...same("colorNeutralForegroundOnBrand")],
  ["--destructive-subtle", ...same("colorStatusDangerBackground1")],
  ["--destructive-text", ...same("colorStatusDangerForeground1")],
  ["--success", ...same("colorStatusSuccessBackground3")],
  ["--success-foreground", ...same("colorNeutralForegroundOnBrand")],
  ["--success-subtle", ...same("colorStatusSuccessBackground1")],
  ["--success-text", ...same("colorStatusSuccessForeground1")],
  ["--warning", ...same("colorStatusWarningBackground3")],
  ["--warning-foreground", "colorNeutralForeground1", "colorNeutralForegroundInverted"],
  ["--warning-subtle", ...same("colorStatusWarningBackground1")],
  ["--warning-text", ...same("colorStatusWarningForeground1")],
  ["--info", ...same("colorBrandBackground")],
  ["--info-foreground", ...same("colorNeutralForegroundOnBrand")],
  ["--info-subtle", ...same("colorBrandBackground2")],
  ["--info-text", ...same("colorBrandForeground1")],
  ["--neutral", ...same("colorNeutralForeground3")],
  ["--neutral-foreground", "colorNeutralForegroundOnBrand", "colorNeutralForegroundInverted"],
  ["--neutral-subtle", "colorNeutralBackground3", "colorNeutralBackground6"],
  ["--neutral-text", ...same("colorNeutralForeground2")],
  ["--border", ...same("colorNeutralStroke2")],
  ["--input", ...same("colorNeutralStroke1")],
  ["--ring", ...same("colorCompoundBrandStroke")],
  ["--chart-1", ...same("colorBrandForeground1")],
  ["--chart-2", ...same("colorPaletteDarkOrangeBorderActive")],
  ["--chart-3", ...same("colorPaletteTealBorderActive")],
  ["--chart-4", ...same("colorPalettePurpleBorderActive")],
  ["--chart-5", ...same("colorPaletteMarigoldBorderActive")],
  ["--link", ...same("colorBrandForegroundLink")],
  ["--link-hover", ...same("colorBrandForegroundLinkHover")],
  ["--link-pressed", ...same("colorBrandForegroundLinkPressed")],
  ["--stroke-brand", ...same("colorCompoundBrandStroke")],
  ["--compound-bg", ...same("colorCompoundBrandBackground")],
  ["--compound-bg-hover", ...same("colorCompoundBrandBackgroundHover")],
  ["--compound-bg-pressed", ...same("colorCompoundBrandBackgroundPressed")],
  ["--on-compound", ...same("colorNeutralForegroundInverted")],
];

// The light rail is only declared for light mode (dark mode has its own rule below).
const railLight = [
  ["--sidebar", "colorNeutralBackground4", null],
  ["--sidebar-foreground", "colorNeutralForeground1", null],
  ["--sidebar-accent", "colorNeutralBackground4Hover", null],
  ["--sidebar-accent-foreground", "colorNeutralForeground1", null],
  ["--sidebar-border", "colorNeutralStroke2", null],
  ["--sidebar-primary", "colorBrandBackground", null],
  ["--sidebar-primary-foreground", "colorNeutralForegroundOnBrand", null],
  ["--sidebar-ring", "colorCompoundBrandStroke", null],
];

// The dark rail in light mode takes its values from the dark theme.
const railDarkTone = [
  ["--sidebar", null, "colorNeutralBackground1"],
  ["--sidebar-foreground", null, "colorNeutralForeground1"],
  ["--sidebar-accent", null, "colorNeutralBackground1Selected"],
  ["--sidebar-accent-foreground", null, "colorNeutralForeground1"],
  ["--sidebar-border", null, "colorNeutralStroke2"],
  ["--sidebar-primary", null, "colorBrandForeground1"],
  ["--sidebar-primary-foreground", null, "colorNeutralForegroundInverted"],
  ["--sidebar-ring", null, "colorCompoundBrandStroke"],
];

// In dark mode the rail is one surface step down, on either tone.
const railDarkMode = [
  ["--sidebar", null, "colorNeutralBackground4"],
  ["--sidebar-foreground", null, "colorNeutralForeground1"],
  ["--sidebar-accent", null, "colorNeutralBackground4Hover"],
  ["--sidebar-accent-foreground", null, "colorNeutralForeground1"],
  ["--sidebar-border", null, "colorNeutralStroke2"],
  ["--sidebar-primary", null, "colorBrandBackground"],
  ["--sidebar-primary-foreground", null, "colorNeutralForegroundOnBrand"],
  ["--sidebar-ring", null, "colorCompoundBrandStroke"],
];

const checks = [
  // [label, selector, rows, which side of each row the rule is compared with]
  ["section 9, light", ":root", section9Light, "light"],
  ["section 9, dark", ".dark", section9, "dark"],
  ["fluent, light", ':root[data-color-preset="fluent"]', [...surfaces, ...railLight], "light"],
  ["fluent, dark", ':root.dark[data-color-preset="fluent"]', surfaces, "dark"],
  [
    "fluent, dark rail on light mode",
    ':root[data-color-preset="fluent"][data-sidebar-tone="dark"]',
    railDarkTone,
    "dark",
  ],
  [
    "fluent, dark mode rail",
    ':root.dark[data-color-preset="fluent"][data-sidebar-tone]',
    railDarkMode,
    "dark",
  ],
];

let bad = 0;
let compared = 0;
for (const [label, selector, rows, mode] of checks) {
  const text = rule(selector);
  if (text === undefined) {
    console.error(`[${label}] no rule \`${selector}\` in theme.css`);
    bad++;
    continue;
  }
  const theme = mode === "light" ? webLightTheme : webDarkTheme;
  for (const [name, lightToken, darkToken] of rows) {
    const token = mode === "light" ? lightToken : darkToken;
    if (token === null) continue;
    const expected = theme[token];
    if (expected === undefined) {
      console.error(`[${label}] ${name}: @fluentui/tokens has no \`${token}\``);
      bad++;
      continue;
    }
    const actual = read(text, name);
    compared++;
    if (norm(actual) !== norm(String(expected))) {
      bad++;
      console.error(`[${label}] ${name}: theme.css = ${actual ?? "(missing)"} · Fluent ${token} = ${expected}`);
    }
  }
}

if (bad) {
  console.error(`\n${bad} token(s) differ from Fluent 2.`);
  process.exit(1);
}
console.log(`All ${compared} Fluent 2 tokens match @fluentui/tokens (light and dark).`);
