/**
 * How a date is written, everywhere.
 *
 * There were two shapes on screen at once and both were right on their own:
 * `toLocaleDateString(…, { month: "short" })` gave "Aug 23, 2026" and a bare
 * `toLocaleString()` gave "8/31/2026, 4:00:24 PM". A detail card showing both,
 * two rows apart, makes the reader work out that they are the same kind of
 * value — and the seconds are noise on a record nobody is timing.
 *
 * Every formatter is pinned to `vi-VN`: the application is Vietnamese only, so a
 * date reads dd/MM/yyyy and a number uses "." for thousands whatever the
 * browser's own locale happens to be.
 */

const LOCALE = "vi-VN";

const DATE: Intl.DateTimeFormatOptions = {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
};

const DATE_TIME: Intl.DateTimeFormatOptions = {
  ...DATE,
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
};

/** The em dash, not an empty cell — a blank reads as a rendering failure. */
export const EMPTY = "—";

export function formatDate(value?: string | null): string {
  if (!value) return EMPTY;
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? EMPTY
    : date.toLocaleDateString(LOCALE, DATE);
}

export function formatDateTime(value?: string | null): string {
  if (!value) return EMPTY;
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? EMPTY
    : date.toLocaleString(LOCALE, DATE_TIME);
}

export function formatNumber(value?: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) return EMPTY;
  return value.toLocaleString(LOCALE);
}

/**
 * Vietnamese dong, written `1.534.528 VND`: the code after the figure, not the ₫ / đ sign `Intl`
 * would print. It has no minor unit in practice, so no decimals.
 */
export function formatCurrency(value?: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) return EMPTY;
  return `${Math.round(value).toLocaleString(LOCALE)} VND`;
}
