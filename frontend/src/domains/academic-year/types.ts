/**
 * The `academic_years` resource as the API reports it — `AcademicYearResponse`
 * on the backend. Kept in step by hand, like the rest of the API contract.
 */
export type AcademicYear = {
  id: number;
  organization: SchoolSummary;
  /** Derived by the API from the two dates, e.g. `2026-2027`. Never sent. */
  name: string;
  startDate: string;
  endDate: string;
  periods: AcademicPeriod[];
  createdAt: string;
  updatedAt: string | null;
};

/** The school as a year reports it. A school is an organization. */
export type SchoolSummary = {
  id: number;
  code: string;
  name: string;
};

/** A term inside the year. */
export type AcademicPeriod = {
  id?: number;
  name: string;
  startDate: string;
  endDate: string;
};

/**
 * What the create and edit forms hold, and what is sent back.
 *
 * `organization.id` is an empty string until somebody picks a school; the
 * request leaves the field out in that case and the API uses the caller's own
 * school. Only a system administrator, who belongs to none, has to choose.
 */
export type AcademicYearFormValues = {
  organization: { id: string };
  startDate: string;
  endDate: string;
  periods: AcademicPeriod[];
};

/** `2026-09-05` -> `2026-2027` is the API's rule; this only previews it. */
export function previewYearName(startDate?: string, endDate?: string): string {
  const start = startDate?.slice(0, 4);
  const end = endDate?.slice(0, 4);
  return start && end ? `${start}-${end}` : "—";
}
