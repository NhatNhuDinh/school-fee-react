/**
 * `GET /fee_registrations/matrix` — everything the registration screen draws for one
 * lớp, as the API reports it (`FeeRegistrationMatrixResponse`). Kept in step by hand,
 * like the rest of the API contract.
 *
 * Not a resource list: the screen is a grid, so the answer is one object — the
 * columns, the rows, and which cells are ticked.
 */
export type FeeRegistrationMatrix = {
  schoolClass: { id: number; name: string; classGroupId: number; classGroupName: string };
  academicYear: { id: number; name: string };
  /** The columns: the khoản thu this lớp may register for. */
  feeItems: FeeRegistrationColumn[];
  /** The rows: the students of the lớp. */
  students: FeeRegistrationStudent[];
  /** The ticked cells. */
  registrations: { enrollmentId: number; feeItemId: number }[];
};

export type FeeRegistrationColumn = {
  id: number;
  name: string;
  priceMode: "SINGLE" | "MULTIPLE";
  basePrice: number | null;
  unit: string;
  vatRate: number;
};

export type FeeRegistrationStudent = {
  /** What a registration is addressed to: the student in this year. */
  enrollmentId: number;
  studentCode: string;
  fullName: string;
};

/** `PUT /fee_registrations/class/{id}`: how many rows a save took. */
export type FeeRegistrationSaveResult = { added: number; removed: number };

/** A cell, as a string so a set of them can be compared. */
export const cellKey = (enrollmentId: number, feeItemId: number) => `${enrollmentId}:${feeItemId}`;

/** The set of ticked cells the server reports. */
export function ticksOf(matrix: FeeRegistrationMatrix): Set<string> {
  return new Set(matrix.registrations.map((r) => cellKey(r.enrollmentId, r.feeItemId)));
}

/** Whether two sets of ticks are the same cells. */
export function sameTicks(a: ReadonlySet<string>, b: ReadonlySet<string>): boolean {
  if (a.size !== b.size) return false;
  for (const key of a) if (!b.has(key)) return false;
  return true;
}

/** The request body of a save: every student with the khoản thu ticked for them. */
export function savePayload(
  matrix: FeeRegistrationMatrix,
  ticks: ReadonlySet<string>
): { registrations: { enrollmentId: number; feeItemIds: number[] }[] } {
  return {
    registrations: matrix.students.map((student) => ({
      enrollmentId: student.enrollmentId,
      feeItemIds: matrix.feeItems
        .filter((item) => ticks.has(cellKey(student.enrollmentId, item.id)))
        .map((item) => item.id),
    })),
  };
}
